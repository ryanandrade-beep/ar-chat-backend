package br.com.archat.dominio.plano;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Vínculo de uma workspace a um plano. Fica EM_ATRASO quando a mensalidade do mês não pôde ser cobrada. */
public class Assinatura {

    public enum Situacao { ATIVA, EM_ATRASO }

    private final UUID clienteId;
    private UUID planoId;
    private Situacao situacao;
    private Instant inicio;
    private Instant ultimaCobranca;

    public Assinatura(UUID clienteId, UUID planoId, Situacao situacao, Instant inicio, Instant ultimaCobranca) {
        this.clienteId = Objects.requireNonNull(clienteId);
        this.planoId = Objects.requireNonNull(planoId);
        this.situacao = Objects.requireNonNull(situacao);
        this.inicio = Objects.requireNonNull(inicio);
        this.ultimaCobranca = ultimaCobranca;
    }

    public static Assinatura nova(UUID clienteId, UUID planoId, Instant agora) {
        return new Assinatura(clienteId, planoId, Situacao.ATIVA, agora, null);
    }

    public void trocarPlano(UUID novoPlanoId, Instant agora) {
        planoId = Objects.requireNonNull(novoPlanoId);
        inicio = agora;
    }

    public void registrarCobranca(Instant agora) {
        situacao = Situacao.ATIVA;
        ultimaCobranca = agora;
    }

    public void registrarAtraso() {
        situacao = Situacao.EM_ATRASO;
    }

    public UUID clienteId() { return clienteId; }
    public UUID planoId() { return planoId; }
    public Situacao situacao() { return situacao; }
    public Instant inicio() { return inicio; }
    public Instant ultimaCobranca() { return ultimaCobranca; }
}
