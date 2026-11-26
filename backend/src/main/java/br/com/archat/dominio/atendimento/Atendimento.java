package br.com.archat.dominio.atendimento;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Um ciclo de atendimento de uma conversa: abre quando o cliente final escreve (ou a empresa inicia),
 * muda de status conforme as mensagens e as ações da empresa, e termina ao ser finalizado.
 * Uma conversa tem vários atendimentos ao longo do tempo, mas só um aberto por vez.
 */
public class Atendimento {

    public static final int TAMANHO_MAXIMO_OBSERVACAO = 500;

    private final UUID id;
    private final UUID conversaId;
    private final UUID clienteId;
    private final OrigemAtendimento origem;
    private StatusAtendimento status;
    private final Instant abertoEm;
    private Instant iniciadoEm;
    private Instant primeiraRespostaEm;
    private Instant finalizadoEm;
    private String observacaoDeEncerramento;
    private int mensagensEnviadas;
    private int mensagensRecebidas;

    private Atendimento(UUID id, UUID conversaId, UUID clienteId, OrigemAtendimento origem, StatusAtendimento status,
                        Instant abertoEm, Instant iniciadoEm, Instant primeiraRespostaEm, Instant finalizadoEm,
                        int mensagensEnviadas, int mensagensRecebidas) {
        this.id = Objects.requireNonNull(id);
        this.conversaId = Objects.requireNonNull(conversaId);
        this.clienteId = Objects.requireNonNull(clienteId);
        this.origem = Objects.requireNonNull(origem);
        this.status = Objects.requireNonNull(status);
        this.abertoEm = Objects.requireNonNull(abertoEm);
        this.iniciadoEm = iniciadoEm;
        this.primeiraRespostaEm = primeiraRespostaEm;
        this.finalizadoEm = finalizadoEm;
        this.observacaoDeEncerramento = null;
        this.mensagensEnviadas = mensagensEnviadas;
        this.mensagensRecebidas = mensagensRecebidas;
    }

    public static Atendimento abertoPeloClienteFinal(UUID conversaId, UUID clienteId, Instant agora) {
        return new Atendimento(UUID.randomUUID(), conversaId, clienteId, OrigemAtendimento.CLIENTE_FINAL,
                StatusAtendimento.AGUARDANDO, agora, null, null, null, 0, 1);
    }

    public static Atendimento abertoPelaEmpresa(UUID conversaId, UUID clienteId, Instant agora) {
        return new Atendimento(UUID.randomUUID(), conversaId, clienteId, OrigemAtendimento.EMPRESA,
                StatusAtendimento.EM_ANDAMENTO, agora, agora, null, null, 1, 0);
    }

    public static Atendimento reconstituir(UUID id, UUID conversaId, UUID clienteId, OrigemAtendimento origem,
                                           StatusAtendimento status, Instant abertoEm, Instant iniciadoEm,
                                           Instant primeiraRespostaEm, Instant finalizadoEm, int mensagensEnviadas,
                                           int mensagensRecebidas) {
        return new Atendimento(id, conversaId, clienteId, origem, status, abertoEm, iniciadoEm, primeiraRespostaEm,
                finalizadoEm, mensagensEnviadas, mensagensRecebidas);
    }

    public static Atendimento reconstituir(UUID id, UUID conversaId, UUID clienteId, OrigemAtendimento origem,
                                           StatusAtendimento status, Instant abertoEm, Instant iniciadoEm,
                                           Instant primeiraRespostaEm, Instant finalizadoEm, int mensagensEnviadas,
                                           int mensagensRecebidas, String observacaoDeEncerramento) {
        Atendimento atendimento = reconstituir(id, conversaId, clienteId, origem, status, abertoEm, iniciadoEm,
                primeiraRespostaEm, finalizadoEm, mensagensEnviadas, mensagensRecebidas);
        atendimento.observacaoDeEncerramento = observacaoDeEncerramento;
        return atendimento;
    }

    public void registrarEnvio(Instant agora) {
        exigirAberto();
        mensagensEnviadas++;
        iniciar(agora);
        if (origem == OrigemAtendimento.CLIENTE_FINAL && primeiraRespostaEm == null) {
            primeiraRespostaEm = agora;
        }
        if (status != StatusAtendimento.EM_ANDAMENTO) {
            status = StatusAtendimento.EM_ANDAMENTO;
        }
    }

    public void iniciar(Instant agora) {
        exigirAberto();
        if (iniciadoEm == null) {
            iniciadoEm = agora;
        }
    }

    public void registrarRecebimento() {
        exigirAberto();
        mensagensRecebidas++;
        status = StatusAtendimento.AGUARDANDO;
    }

    public void colocarEmEspera() {
        mudarPara(StatusAtendimento.EM_ESPERA);
    }

    public void retomar() {
        if (status != StatusAtendimento.EM_ESPERA) {
            throw new RegraDeNegocioException("Só um atendimento em espera pode ser retomado");
        }
        mudarPara(StatusAtendimento.EM_ANDAMENTO);
    }

    public void finalizar(Instant agora) {
        finalizar(agora, null);
    }

    public void finalizar(Instant agora, String observacao) {
        String limpa = observacao == null ? null : observacao.strip();
        if (limpa != null && limpa.length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new RegraDeNegocioException("A observação deve ter no máximo %d caracteres".formatted(TAMANHO_MAXIMO_OBSERVACAO));
        }
        mudarPara(StatusAtendimento.FINALIZADO);
        finalizadoEm = agora;
        observacaoDeEncerramento = limpa == null || limpa.isEmpty() ? null : limpa;
    }

    public void devolverParaFila() {
        mudarPara(StatusAtendimento.AGUARDANDO);
    }

    public Optional<Duration> tempoAtePrimeiraResposta() {
        return Optional.ofNullable(primeiraRespostaEm).map(resposta -> Duration.between(abertoEm, resposta));
    }

    public Optional<Duration> tempoDeEspera() {
        return Optional.ofNullable(iniciadoEm).map(inicio -> Duration.between(abertoEm, inicio));
    }

    public Optional<Duration> tempoDeAtendimento() {
        if (iniciadoEm == null || finalizadoEm == null) {
            return Optional.empty();
        }
        return Optional.of(Duration.between(iniciadoEm, finalizadoEm));
    }

    private void mudarPara(StatusAtendimento destino) {
        if (!status.podeIrPara(destino)) {
            throw new RegraDeNegocioException("Atendimento não pode passar de %s para %s".formatted(status, destino));
        }
        status = destino;
    }

    private void exigirAberto() {
        if (!status.aberto()) {
            throw new RegraDeNegocioException("Atendimento já finalizado");
        }
    }

    public UUID id() { return id; }
    public UUID conversaId() { return conversaId; }
    public UUID clienteId() { return clienteId; }
    public OrigemAtendimento origem() { return origem; }
    public StatusAtendimento status() { return status; }
    public Instant abertoEm() { return abertoEm; }
    public Instant iniciadoEm() { return iniciadoEm; }
    public Instant primeiraRespostaEm() { return primeiraRespostaEm; }
    public Instant finalizadoEm() { return finalizadoEm; }
    public String observacaoDeEncerramento() { return observacaoDeEncerramento; }
    public int mensagensEnviadas() { return mensagensEnviadas; }
    public int mensagensRecebidas() { return mensagensRecebidas; }
}
