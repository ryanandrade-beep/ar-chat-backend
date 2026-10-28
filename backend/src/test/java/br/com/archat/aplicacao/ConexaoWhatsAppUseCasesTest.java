package br.com.archat.aplicacao;

import br.com.archat.aplicacao.atendimento.GerenciarAtendimentoUseCase;
import br.com.archat.aplicacao.atendimento.ReceberMensagemUseCase;
import br.com.archat.aplicacao.atendimento.RegistroDeAtendimento;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.aplicacao.whatsapp.GerenciarConexaoWhatsAppUseCase;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConexaoWhatsAppUseCasesTest {

    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private final CanalFalso canal = new CanalFalso();

    @Test
    void codigoPorTelefoneSoComNumeroValidoEWhatsAppDesconectado() {
        GerenciarConexaoWhatsAppUseCase conexao = new GerenciarConexaoWhatsAppUseCase(canal);

        assertThat(conexao.codigoPorTelefone("+55 (44) 99729-4996")).isEqualTo("CODIGO-5544997294996");
        assertThatThrownBy(() -> conexao.codigoPorTelefone("123")).isInstanceOf(RegraDeNegocioException.class);
        canal.conectado = true;
        assertThatThrownBy(() -> conexao.codigoPorTelefone("5544997294996")).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void lerAConversaAvisaOWhatsAppDaUltimaMensagemRecebida() {
        Fakes.Destinatarios destinatarios = new Fakes.Destinatarios();
        Fakes.Conversas conversas = new Fakes.Conversas();
        Fakes.Mensagens mensagens = new Fakes.Mensagens();
        Fakes.Atendimentos atendimentos = new Fakes.Atendimentos();
        UUID mercado = UUID.randomUUID();
        Destinatario maria = Destinatario.novo(mercado, "Maria", "5544999990000", true);
        destinatarios.salvar(maria);
        ReceberMensagemUseCase receber = new ReceberMensagemUseCase(destinatarios, conversas, mensagens,
                new RegistroDeAtendimento(atendimentos), Fakes.SEM_TRANSACAO, relogio);
        receber.doCanal("5544999990000", "Oi", "Z1", relogio.instant().minusSeconds(60));
        receber.doCanal("5544999990000", "Tem pão?", "Z2", relogio.instant());
        Conversa conversa = conversas.buscarPorDestinatario(maria.id()).orElseThrow();

        new GerenciarAtendimentoUseCase(conversas, atendimentos, Fakes.SEM_TRANSACAO, relogio, mensagens, destinatarios,
                canal, null, null).marcarComoLida(mercado, conversa.id());

        assertThat(canal.lidas).containsExactly("5544999990000:Z2");
        assertThat(conversas.buscarPorId(conversa.id()).orElseThrow().naoLidas()).isZero();
    }

    @Test
    void falhaAoAvisarOWhatsAppNaoDesfazALeitura() {
        Fakes.Destinatarios destinatarios = new Fakes.Destinatarios();
        Fakes.Conversas conversas = new Fakes.Conversas();
        Fakes.Mensagens mensagens = new Fakes.Mensagens();
        Fakes.Atendimentos atendimentos = new Fakes.Atendimentos();
        UUID mercado = UUID.randomUUID();
        Destinatario maria = Destinatario.novo(mercado, "Maria", "5544999990000", true);
        destinatarios.salvar(maria);
        new ReceberMensagemUseCase(destinatarios, conversas, mensagens, new RegistroDeAtendimento(atendimentos),
                Fakes.SEM_TRANSACAO, relogio).doCanal("5544999990000", "Oi", "Z1", relogio.instant());
        Conversa conversa = conversas.buscarPorDestinatario(maria.id()).orElseThrow();
        canal.falhar = true;

        Conversa lida = new GerenciarAtendimentoUseCase(conversas, atendimentos, Fakes.SEM_TRANSACAO, relogio, mensagens,
                destinatarios, canal, null, null).marcarComoLida(mercado, conversa.id());

        assertThat(lida.naoLidas()).isZero();
    }

    @Test
    void canalForaDoArApareceDesconectadoSemQuebrarALista() {
        canal.situacaoFalha = true;
        var canais = new br.com.archat.aplicacao.canal.ListarCanaisUseCase(canal).listar();
        assertThat(canais).first().satisfies(c -> assertThat(c.conectado()).isFalse());
    }

    @Test
    void importaDaAgendaSemDuplicarEComOfertasDesligadas() {
        Fakes.Destinatarios destinatarios = new Fakes.Destinatarios();
        UUID mercado = UUID.randomUUID();
        destinatarios.salvar(Destinatario.novo(mercado, "Maria", "44999990000", true));
        canal.agenda = List.of(new ConexaoWhatsApp.ContatoWhatsApp("5544999990000", "Maria"),
                new ConexaoWhatsApp.ContatoWhatsApp("5544988887777", "João"));
        var importar = new br.com.archat.aplicacao.destinatario.ImportarContatosUseCase(canal, destinatarios, Fakes.SEM_TRANSACAO);

        assertThat(importar.listar(mercado, 1, 50)).extracting(c -> c.jaCadastrado()).containsExactly(true, false);
        var resultado = importar.importar(mercado, canal.agenda);

        assertThat(resultado.importados()).isEqualTo(1);
        assertThat(resultado.ignorados()).isEqualTo(1);
        assertThat(destinatarios.listarPorCliente(mercado)).filteredOn(d -> d.nome().equals("João"))
                .singleElement().satisfies(d -> assertThat(d.aceitaOfertas()).isFalse());
    }

    static class CanalFalso implements ConexaoWhatsApp {
        boolean conectado;
        boolean falhar;
        boolean situacaoFalha;
        final List<String> lidas = new ArrayList<>();

        public SituacaoConexao situacao() {
            if (situacaoFalha) throw new IllegalStateException("canal fora do ar");
            return new SituacaoConexao("falso", conectado, conectado, null);
        }
        public String qrCode() { return "data:image/png;base64,AAA"; }
        public String codigoPorTelefone(String telefone) { return "CODIGO-" + telefone; }
        public void marcarComoLida(String telefoneOuGrupo, String idExterno) {
            if (falhar) {
                throw new IllegalStateException("canal fora do ar");
            }
            lidas.add(telefoneOuGrupo + ":" + idExterno);
        }
        final List<String> avisos = new ArrayList<>();
        List<ContatoWhatsApp> agenda = List.of();
        public void enviarAvisoDoSistema(String telefone, String texto) {
            if (falhar) {
                throw new IllegalStateException("canal fora do ar");
            }
            avisos.add(telefone + ":" + texto);
        }
        public List<ContatoWhatsApp> listarContatos(int pagina, int tamanhoPagina) { return agenda; }
        public void desconectar() { }
        public List<GrupoWhatsApp> listarGrupos(int pagina, int tamanhoPagina) { return List.of(); }
    }
}
