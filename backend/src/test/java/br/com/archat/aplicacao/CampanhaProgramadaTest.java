package br.com.archat.aplicacao;

import br.com.archat.aplicacao.campanha.DispararCampanhaUseCase;
import br.com.archat.aplicacao.campanha.LiberarMensagensAgendadasUseCase;
import br.com.archat.aplicacao.destinatario.GerenciarEtiquetasUseCase;
import br.com.archat.aplicacao.porta.EnvioDeCampanhas;
import br.com.archat.aplicacao.porta.EtiquetasDosContatos;
import br.com.archat.aplicacao.porta.LiberacoesDeMensagens;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.Documento;
import br.com.archat.dominio.cliente.TipoDocumento;
import br.com.archat.dominio.cliente.TipoPlano;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Prioridade;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CampanhaProgramadaTest {

    private final Instant inicio = Instant.parse("2026-10-09T12:00:00Z");
    private Instant agora = inicio;
    private final Clock relogio = new Clock() {
        public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        public Clock withZone(java.time.ZoneId zone) { return this; }
        public Instant instant() { return agora; }
    };

    static class Etiquetas implements EtiquetasDosContatos {
        final Map<UUID, List<String>> dados = new HashMap<>();
        final Map<UUID, UUID> donos = new HashMap<>();
        public Map<UUID, List<String>> doCliente(UUID clienteId) { return dados; }
        public void definir(UUID destinatarioId, List<String> etiquetas) { dados.put(destinatarioId, etiquetas); }
        public Set<UUID> comEtiqueta(UUID clienteId, String etiqueta) {
            Set<UUID> ids = new HashSet<>();
            dados.forEach((id, lista) -> { if (lista.contains(etiqueta)) ids.add(id); });
            return ids;
        }
    }

    static class Envios implements EnvioDeCampanhas {
        final Map<UUID, Envio> dados = new HashMap<>();
        public void salvar(Envio envio) { dados.put(envio.campanhaId(), envio); }
        public Map<UUID, Envio> buscar(Collection<UUID> ids) { return dados; }
    }

    static class Liberacoes implements LiberacoesDeMensagens {
        final Map<UUID, Instant> dados = new LinkedHashMap<>();
        public void agendar(UUID id, Instant quando) { dados.put(id, quando); }
        public List<UUID> vencidas(Instant agora, int limite) {
            return dados.entrySet().stream().filter(e -> !e.getValue().isAfter(agora)).map(Map.Entry::getKey)
                    .limit(limite).toList();
        }
        public void remover(UUID id) { dados.remove(id); }
        public boolean existe(UUID id) { return dados.containsKey(id); }
    }

    private final Fakes.Clientes clientes = new Fakes.Clientes();
    private final Fakes.Destinatarios destinatarios = new Fakes.Destinatarios();
    private final Fakes.Conversas conversas = new Fakes.Conversas();
    private final Fakes.Mensagens mensagens = new Fakes.Mensagens();
    private final Fakes.Fila fila = new Fakes.Fila();
    private final Etiquetas etiquetas = new Etiquetas();
    private final Envios envios = new Envios();
    private final Liberacoes liberacoes = new Liberacoes();

    private final DispararCampanhaUseCase disparar = new DispararCampanhaUseCase(clientes, destinatarios, conversas,
            mensagens, new Fakes.Transacoes(), new Fakes.Campanhas(), fila, Fakes.SEM_TRANSACAO, relogio, null,
            etiquetas, envios, liberacoes);
    private final LiberarMensagensAgendadasUseCase liberar =
            new LiberarMensagensAgendadasUseCase(liberacoes, mensagens, fila, Fakes.SEM_TRANSACAO, relogio);

    private Cliente cliente() {
        Cliente c = Cliente.novo("Mercado", new Documento("11222333000181", TipoDocumento.CNPJ), TipoPlano.PRE_PAGO,
                new BigDecimal("50.00"), inicio);
        clientes.salvar(c);
        return c;
    }

    private Destinatario contato(Cliente c, String nome, String telefone, String... tags) {
        Destinatario d = Destinatario.novo(c.id(), nome, telefone, true);
        destinatarios.salvar(d);
        if (tags.length > 0) {
            etiquetas.definir(d.id(), List.of(tags));
        }
        return d;
    }

    private DispararCampanhaUseCase.Comando comando(Cliente c, Instant quando, int pausa, String etiqueta) {
        return new DispararCampanhaUseCase.Comando(c.id(), "Ofertas", "Picanha", null, Prioridade.NORMAL, null, true,
                quando, pausa, etiqueta);
    }

    @Test
    void etiquetaFiltraOPublicoDaCampanha() {
        Cliente c = cliente();
        contato(c, "Ana", "44999990001", "vip");
        contato(c, "Bruno", "44999990002", "vip", "centro");
        contato(c, "Carla", "44999990003", "centro");
        contato(c, "Dino", "44999990004");

        var disparada = disparar.executar(comando(c, null, 0, "vip"));

        assertThat(disparada.campanha().totalDestinatarios()).isEqualTo(2);
        assertThat(envios.dados.get(disparada.campanha().id()).etiqueta()).isEqualTo("vip");
    }

    @Test
    void semProgramacaoTudoVaiParaAFilaNaHora() {
        Cliente c = cliente();
        contato(c, "Ana", "44999990001");
        contato(c, "Bruno", "44999990002");

        disparar.executar(comando(c, null, 0, null));

        assertThat(fila.itens).hasSize(2);
        assertThat(liberacoes.dados).isEmpty();
    }

    @Test
    void pausaEntreMensagensRetemAsSeguintesELiberaNoHorario() {
        Cliente c = cliente();
        contato(c, "Ana", "44999990001");
        contato(c, "Bruno", "44999990002");
        contato(c, "Carla", "44999990003");

        var disparada = disparar.executar(comando(c, null, 30, null));

        assertThat(fila.itens).hasSize(1);
        assertThat(liberacoes.dados.values()).containsExactly(inicio.plusSeconds(30), inicio.plusSeconds(60));
        assertThat(disparada.campanha().totalDestinatarios()).isEqualTo(3);

        agora = inicio.plusSeconds(31);
        assertThat(liberar.executar()).isEqualTo(1);
        assertThat(fila.itens).hasSize(2);

        agora = inicio.plusSeconds(61);
        assertThat(liberar.executar()).isEqualTo(1);
        assertThat(fila.itens).hasSize(3);
        assertThat(liberacoes.dados).isEmpty();
    }

    @Test
    void campanhaAgendadaNaoEnfileiraNadaAteOHorario() {
        Cliente c = cliente();
        contato(c, "Ana", "44999990001");
        contato(c, "Bruno", "44999990002");
        Instant amanha = inicio.plus(Duration.ofHours(24));

        var disparada = disparar.executar(comando(c, amanha, 10, null));

        assertThat(fila.itens).isEmpty();
        assertThat(envios.dados.get(disparada.campanha().id()).agendadaPara()).isEqualTo(amanha);
        assertThat(liberar.executar()).isZero();

        agora = amanha.plusSeconds(1);
        assertThat(liberar.executar()).isEqualTo(1);
        agora = amanha.plusSeconds(11);
        assertThat(liberar.executar()).isEqualTo(1);
        assertThat(fila.itens).hasSize(2);
    }

    @Test
    void recusaHorarioNoPassadoPausaInvalidaEAgendamentoMuitoLonge() {
        Cliente c = cliente();
        contato(c, "Ana", "44999990001");

        assertThatThrownBy(() -> disparar.executar(comando(c, inicio.minusSeconds(3600), 0, null)))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("passou");
        assertThatThrownBy(() -> disparar.executar(comando(c, null, -1, null)))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> disparar.executar(comando(c, null, 3601, null)))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> disparar.executar(comando(c, inicio.plus(Duration.ofDays(91)), 0, null)))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("90 dias");
    }

    @Test
    void etiquetaEhNormalizadaEValidada() {
        assertThat(GerenciarEtiquetasUseCase.normalizar("  VIP   Ouro ")).isEqualTo("vip ouro");
        assertThat(GerenciarEtiquetasUseCase.normalizar("Açougue-Centro")).isEqualTo("açougue-centro");
        for (String ruim : List.of("", "@vip", "vip#1", "<b>", "a".repeat(31))) {
            assertThatThrownBy(() -> GerenciarEtiquetasUseCase.normalizar(ruim)).as(ruim)
                    .isInstanceOf(RegraDeNegocioException.class);
        }
    }
}
