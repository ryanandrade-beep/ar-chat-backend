package br.com.archat.dominio;

import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.atendimento.StatusAtendimento;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AtendimentoTest {

    private final Instant t0 = Instant.parse("2026-10-13T12:00:00Z");

    private Atendimento doClienteFinal() {
        return Atendimento.abertoPeloClienteFinal(UUID.randomUUID(), UUID.randomUUID(), t0);
    }

    @Test
    void mensagemDoClienteAbreAguardandoERespostaPoeEmAndamento() {
        Atendimento a = doClienteFinal();
        assertThat(a.status()).isEqualTo(StatusAtendimento.AGUARDANDO);

        a.registrarEnvio(t0.plusSeconds(90));

        assertThat(a.status()).isEqualTo(StatusAtendimento.EM_ANDAMENTO);
        assertThat(a.tempoAtePrimeiraResposta()).contains(Duration.ofSeconds(90));
    }

    @Test
    void novaMensagemDoClienteVoltaParaAguardando() {
        Atendimento a = doClienteFinal();
        a.registrarEnvio(t0.plusSeconds(60));
        a.registrarRecebimento();
        assertThat(a.status()).isEqualTo(StatusAtendimento.AGUARDANDO);
        assertThat(a.mensagensRecebidas()).isEqualTo(2);
    }

    @Test
    void esperaERetomada() {
        Atendimento a = doClienteFinal();
        a.colocarEmEspera();
        assertThat(a.status()).isEqualTo(StatusAtendimento.EM_ESPERA);
        a.retomar();
        assertThat(a.status()).isEqualTo(StatusAtendimento.EM_ANDAMENTO);
    }

    @Test
    void soRetomaQuemEstaEmEspera() {
        assertThatThrownBy(() -> doClienteFinal().retomar()).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void finalizadoNaoAceitaMaisMudancas() {
        Atendimento a = doClienteFinal();
        a.finalizar(t0.plusSeconds(600));
        assertThat(a.tempoDeAtendimento()).isEmpty();
        assertThatThrownBy(a::colocarEmEspera).isInstanceOf(RegraDeNegocioException.class);
        assertThatThrownBy(() -> a.registrarEnvio(t0)).isInstanceOf(RegraDeNegocioException.class);
    }

    @Test
    void esperaEAtendimentoSaoMedidosSeparados() {
        Atendimento a = doClienteFinal();
        assertThat(a.tempoDeEspera()).isEmpty();

        a.iniciar(t0.plusSeconds(120));
        a.iniciar(t0.plusSeconds(300));
        a.registrarEnvio(t0.plusSeconds(400));
        a.finalizar(t0.plusSeconds(720));

        assertThat(a.tempoDeEspera()).contains(Duration.ofMinutes(2));
        assertThat(a.tempoAtePrimeiraResposta()).contains(Duration.ofSeconds(400));
        assertThat(a.tempoDeAtendimento()).contains(Duration.ofMinutes(10));
    }

    @Test
    void primeiraRespostaIniciaOAtendimentoSeNinguemPuxou() {
        Atendimento a = doClienteFinal();
        a.registrarEnvio(t0.plusSeconds(90));
        assertThat(a.tempoDeEspera()).contains(Duration.ofSeconds(90));
    }

    @Test
    void iniciadoPelaEmpresaNaoTemTempoDePrimeiraResposta() {
        Atendimento a = Atendimento.abertoPelaEmpresa(UUID.randomUUID(), UUID.randomUUID(), t0);
        assertThat(a.status()).isEqualTo(StatusAtendimento.EM_ANDAMENTO);
        a.registrarRecebimento();
        a.registrarEnvio(t0.plusSeconds(30));
        assertThat(a.tempoAtePrimeiraResposta()).isEmpty();
    }
}
