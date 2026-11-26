package br.com.archat.aplicacao;

import br.com.archat.aplicacao.atendimento.GerenciarAtendimentoUseCase;
import br.com.archat.aplicacao.atendimento.ReceberMensagemUseCase;
import br.com.archat.aplicacao.atendimento.RegistroDeAtendimento;
import br.com.archat.aplicacao.campanha.DispararCampanhaUseCase;
import br.com.archat.aplicacao.mensagem.AtualizarStatusPeloCanalUseCase;
import br.com.archat.aplicacao.mensagem.AtualizarStatusPeloCanalUseCase.AvisoDoCanal;
import br.com.archat.aplicacao.mensagem.EnviarMensagemUseCase;
import br.com.archat.dominio.atendimento.StatusAtendimento;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.Documento;
import br.com.archat.dominio.cliente.TipoDocumento;
import br.com.archat.dominio.cliente.TipoPlano;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Prioridade;
import br.com.archat.dominio.mensagem.StatusMensagem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AtendimentoUseCasesTest {

    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private final Fakes.Clientes clientes = new Fakes.Clientes();
    private final Fakes.Destinatarios destinatarios = new Fakes.Destinatarios();
    private final Fakes.Conversas conversas = new Fakes.Conversas();
    private final Fakes.Mensagens mensagens = new Fakes.Mensagens();
    private final Fakes.Transacoes transacoes = new Fakes.Transacoes();
    private final Fakes.Atendimentos atendimentos = new Fakes.Atendimentos();
    private final Fakes.Fila fila = new Fakes.Fila();
    private final RegistroDeAtendimento registro = new RegistroDeAtendimento(atendimentos);

    private final EnviarMensagemUseCase enviar = new EnviarMensagemUseCase(clientes, destinatarios, conversas, mensagens,
            transacoes, fila, Fakes.SEM_TRANSACAO, relogio, Duration.ofSeconds(1), registro);
    private final ReceberMensagemUseCase receber = new ReceberMensagemUseCase(destinatarios, conversas, mensagens,
            registro, Fakes.SEM_TRANSACAO, relogio);
    private final GerenciarAtendimentoUseCase gerenciar = new GerenciarAtendimentoUseCase(conversas, atendimentos,
            Fakes.SEM_TRANSACAO, relogio);

    private Cliente mercado;
    private Destinatario maria;

    @BeforeEach
    void preparar() {
        mercado = Cliente.novo("Mercado", new Documento("52998224725", TipoDocumento.CPF), TipoPlano.PRE_PAGO,
                new BigDecimal("10.00"), relogio.instant());
        clientes.salvar(mercado);
        maria = Destinatario.novo(mercado.id(), "Maria", "5544999990000", true);
        destinatarios.salvar(maria);
    }

    private Conversa conversaDaMaria() {
        return conversas.buscarPorDestinatario(maria.id()).orElseThrow();
    }

    @Test
    void mensagemRecebidaAbreAtendimentoAguardandoEContaComoNaoLida() {
        receber.doCanal("5544999990000", "Meu pedido já saiu?", "Z1", relogio.instant());

        Conversa c = conversaDaMaria();
        assertThat(c.statusAtendimento()).isEqualTo(StatusAtendimento.AGUARDANDO);
        assertThat(c.naoLidas()).isEqualTo(1);
        assertThat(mensagens.dados.values()).singleElement().satisfies(m -> {
            assertThat(m.custo()).isEqualByComparingTo("0");
            assertThat(m.status()).isEqualTo(StatusMensagem.ENTREGUE);
        });
        assertThat(fila.itens).isEmpty();
    }

    @Test
    void contatoCadastradoSemDdiTambemEhEncontrado() {
        Destinatario joao = Destinatario.novo(mercado.id(), "João", "44988887777", false);
        destinatarios.salvar(joao);

        assertThat(receber.doCanal("5544988887777", "Oi", "Z2", relogio.instant())).isPresent();
    }

    @Test
    void remetenteDesconhecidoDeUmaEmpresaViraContatoEAparecenoChat() {
        var mensagem = receber.doCanal(mercado.id(), "5511911112222", "Maria", "Quem é?", null, "Z9", relogio.instant());

        assertThat(mensagem).isPresent();
        var cadastrado = destinatarios.buscarPorClienteETelefone(mercado.id(), "5511911112222");
        assertThat(cadastrado).isPresent();
        assertThat(cadastrado.get().nome()).isEqualTo("Maria");
        assertThat(cadastrado.get().aceitaOfertas()).isFalse();
    }

    @Test
    void mensagemRepetidaOuDeNumeroDesconhecidoEhIgnorada() {
        receber.doCanal("5544999990000", "Oi", "Z1", relogio.instant());

        assertThat(receber.doCanal("5544999990000", "Oi", "Z1", relogio.instant())).isEmpty();
        assertThat(receber.doCanal("5511911112222", "Quem é?", "Z9", relogio.instant())).isEmpty();
        assertThat(mensagens.dados).hasSize(1);
    }

    @Test
    void respostaDaEmpresaPoeEmAndamentoEFinalizarEncerra() {
        receber.doCanal("5544999990000", "Meu pedido já saiu?", "Z1", relogio.instant());
        Conversa c = conversaDaMaria();

        enviar.executar(new EnviarMensagemUseCase.Comando(mercado.id(), c.id(), null, "Saiu agora!", Prioridade.URGENTE));
        assertThat(conversaDaMaria().statusAtendimento()).isEqualTo(StatusAtendimento.EM_ANDAMENTO);

        gerenciar.colocarEmEspera(mercado.id(), c.id());
        assertThat(conversaDaMaria().statusAtendimento()).isEqualTo(StatusAtendimento.EM_ESPERA);

        gerenciar.finalizar(mercado.id(), c.id());
        assertThat(conversaDaMaria().statusAtendimento()).isEqualTo(StatusAtendimento.FINALIZADO);
        assertThat(atendimentos.dados.values()).singleElement()
                .satisfies(a -> assertThat(a.mensagensEnviadas()).isEqualTo(1));

        receber.doCanal("5544999990000", "Obrigada!", "Z2", relogio.instant());
        assertThat(conversaDaMaria().statusAtendimento()).isEqualTo(StatusAtendimento.AGUARDANDO);
        assertThat(atendimentos.dados).hasSize(2);
    }

    @Test
    void campanhaDeMarketingNaoAbreAtendimento() {
        new DispararCampanhaUseCase(clientes, destinatarios, conversas, mensagens, transacoes, new Fakes.Campanhas(), fila,
                Fakes.SEM_TRANSACAO, relogio).executar(new DispararCampanhaUseCase.Comando(mercado.id(), "Ofertas",
                "Picanha", Prioridade.NORMAL, List.of(maria.id()), false));

        assertThat(atendimentos.dados).isEmpty();
        assertThat(conversaDaMaria().statusAtendimento()).isEqualTo(StatusAtendimento.FINALIZADO);
    }

    @Test
    void avisosDoCanalMarcamEntregueELida() {
        Mensagem m = Mensagem.novaDoCliente(java.util.UUID.randomUUID(), "Oi", Prioridade.NORMAL, relogio.instant());
        m.iniciarProcessamento(relogio.instant());
        m.confirmarEnvio("ZAPI-123", relogio.instant());
        mensagens.salvar(m);
        var atualizar = new AtualizarStatusPeloCanalUseCase(mensagens, Fakes.SEM_TRANSACAO, relogio);

        assertThat(atualizar.aplicar(List.of("ZAPI-123"), AvisoDoCanal.ENTREGUE)).isEqualTo(1);
        assertThat(m.status()).isEqualTo(StatusMensagem.ENTREGUE);
        assertThat(atualizar.aplicar(List.of("ZAPI-123", "OUTRA"), AvisoDoCanal.LIDA)).isEqualTo(1);
        assertThat(m.status()).isEqualTo(StatusMensagem.LIDA);
        assertThat(atualizar.aplicar(List.of("ZAPI-123"), AvisoDoCanal.LIDA)).isZero();
    }
}
