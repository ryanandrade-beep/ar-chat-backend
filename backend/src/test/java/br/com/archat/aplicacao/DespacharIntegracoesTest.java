package br.com.archat.aplicacao;

import br.com.archat.aplicacao.integracao.DespacharIntegracoesUseCase;
import br.com.archat.aplicacao.porta.EnvioDeWebhook;
import br.com.archat.aplicacao.porta.IntegracaoRepositorio;
import br.com.archat.dominio.integracao.ConfiguracaoDeIntegracao;
import br.com.archat.dominio.integracao.EnvioDeIntegracao;
import br.com.archat.dominio.integracao.EnvioDeIntegracao.Status;
import br.com.archat.dominio.integracao.EventoDeIntegracao;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DespacharIntegracoesTest {

    private final UUID empresa = UUID.randomUUID();
    private final Instant inicio = Instant.parse("2026-11-25T12:00:00Z");
    private Instant agora = inicio;
    private final Clock relogio = new Clock() {
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return agora; }
    };

    private final Map<UUID, EnvioDeIntegracao> envios = new HashMap<>();
    private final IntegracaoRepositorio repositorio = new IntegracaoRepositorio() {
        @Override public Optional<ConfiguracaoDeIntegracao> buscarConfiguracao(UUID id) {
            return Optional.of(new ConfiguracaoDeIntegracao(empresa, "https://crm.exemplo.com/hook", "whsec_abc",
                    Set.of(EventoDeIntegracao.ATENDIMENTO_FINALIZADO), true, inicio));
        }
        @Override public void salvar(ConfiguracaoDeIntegracao c) { }
        @Override public void salvar(EnvioDeIntegracao e) { envios.put(e.id(), e); }
        @Override public Optional<EnvioDeIntegracao> buscarEnvio(UUID id) { return Optional.ofNullable(envios.get(id)); }
        @Override public List<EnvioDeIntegracao> pendentes(Instant ate, int limite) {
            return envios.values().stream().filter(e -> e.status() == Status.PENDENTE && !e.proximaTentativa().isAfter(ate)).toList();
        }
        @Override public List<EnvioDeIntegracao> ultimos(UUID id, int limite) { return new ArrayList<>(envios.values()); }
    };

    private boolean responderComSucesso = true;
    private int chamadas = 0;
    private final EnvioDeWebhook webhook = (url, segredo, id, evento, corpo) -> {
        chamadas++;
        return responderComSucesso ? new EnvioDeWebhook.Resposta(true, 200, "HTTP 200")
                : new EnvioDeWebhook.Resposta(false, 500, "HTTP 500");
    };
    private final DespacharIntegracoesUseCase despachar = new DespacharIntegracoesUseCase(repositorio, webhook, relogio);

    private EnvioDeIntegracao novoEnvio() {
        EnvioDeIntegracao e = EnvioDeIntegracao.novo(UUID.randomUUID(), empresa, "attendance.finished", "{}", agora);
        repositorio.salvar(e);
        return e;
    }

    @Test
    void sucessoMarcaComoEnviadoENaoTentaDeNovo() {
        EnvioDeIntegracao e = novoEnvio();
        assertThat(despachar.processarPendentes()).isEqualTo(1);
        assertThat(e.status()).isEqualTo(Status.ENVIADO);
        assertThat(e.httpStatus()).isEqualTo(200);
        assertThat(despachar.processarPendentes()).isZero();
        assertThat(chamadas).isEqualTo(1);
    }

    @Test
    void falhaAgendaNovasTentativasComEsperaCrescenteEDepoisDesiste() {
        responderComSucesso = false;
        EnvioDeIntegracao e = novoEnvio();
        despachar.processarPendentes();
        assertThat(e.status()).isEqualTo(Status.PENDENTE);
        assertThat(e.proximaTentativa()).isEqualTo(inicio.plus(Duration.ofSeconds(30)));
        assertThat(despachar.processarPendentes()).as("ainda não é hora").isZero();

        agora = inicio.plusSeconds(31);
        despachar.processarPendentes();
        assertThat(e.proximaTentativa()).isEqualTo(agora.plus(Duration.ofSeconds(120)));

        agora = agora.plusSeconds(121);
        despachar.processarPendentes();
        assertThat(e.proximaTentativa()).isEqualTo(agora.plus(Duration.ofSeconds(600)));

        agora = agora.plusSeconds(601);
        despachar.processarPendentes();
        assertThat(e.status()).isEqualTo(Status.FALHOU);
        assertThat(e.tentativas()).isEqualTo(EnvioDeIntegracao.MAXIMO_DE_TENTATIVAS);
        assertThat(despachar.processarPendentes()).isZero();
    }

    @Test
    void testeNaoTentaDeNovoEReenviarVoltaParaAFila() {
        responderComSucesso = false;
        EnvioDeIntegracao e = despachar.tentar(novoEnvio(), false);
        assertThat(e.status()).isEqualTo(Status.FALHOU);
        assertThat(e.resposta()).isEqualTo("HTTP 500");

        e.reenviar(agora);
        assertThat(e.status()).isEqualTo(Status.PENDENTE);
        assertThat(e.tentativas()).isZero();
        responderComSucesso = true;
        despachar.processarPendentes();
        assertThat(e.status()).isEqualTo(Status.ENVIADO);
    }
}
