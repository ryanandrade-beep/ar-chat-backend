package br.com.archat.dominio.integracao;

import java.time.Instant;
import java.util.UUID;

/** Um aviso a entregar: nasce pendente na mesma transação do fato e é enviado em segundo plano, com novas tentativas. */
public final class EnvioDeIntegracao {
    public enum Status { PENDENTE, ENVIADO, FALHOU }

    public static final int MAXIMO_DE_TENTATIVAS = 4;
    private static final long[] ESPERA_EM_SEGUNDOS = {30, 120, 600};

    private final UUID id;
    private final UUID clienteId;
    private final String evento;
    private final String payload;
    private final Instant criadoEm;
    private Status status;
    private int tentativas;
    private Integer httpStatus;
    private String resposta;
    private Instant enviadoEm;
    private Instant proximaTentativa;

    private EnvioDeIntegracao(UUID id, UUID clienteId, String evento, String payload, Instant criadoEm, Status status,
                              int tentativas, Integer httpStatus, String resposta, Instant enviadoEm, Instant proximaTentativa) {
        this.id = id;
        this.clienteId = clienteId;
        this.evento = evento;
        this.payload = payload;
        this.criadoEm = criadoEm;
        this.status = status;
        this.tentativas = tentativas;
        this.httpStatus = httpStatus;
        this.resposta = resposta;
        this.enviadoEm = enviadoEm;
        this.proximaTentativa = proximaTentativa;
    }

    public static EnvioDeIntegracao novo(UUID id, UUID clienteId, String evento, String payload, Instant agora) {
        return new EnvioDeIntegracao(id, clienteId, evento, payload, agora, Status.PENDENTE, 0, null, null, null, agora);
    }

    public static EnvioDeIntegracao reconstituir(UUID id, UUID clienteId, String evento, String payload, Instant criadoEm,
                                                 Status status, int tentativas, Integer httpStatus, String resposta,
                                                 Instant enviadoEm, Instant proximaTentativa) {
        return new EnvioDeIntegracao(id, clienteId, evento, payload, criadoEm, status, tentativas, httpStatus, resposta,
                enviadoEm, proximaTentativa);
    }

    public void registrarSucesso(int http, String resumo, Instant agora) {
        tentativas++;
        status = Status.ENVIADO;
        httpStatus = http;
        resposta = limitar(resumo);
        enviadoEm = agora;
        proximaTentativa = null;
    }

    public void registrarFalha(Integer http, String motivo, Instant agora, boolean permitirNovaTentativa) {
        tentativas++;
        httpStatus = http;
        resposta = limitar(motivo);
        if (permitirNovaTentativa && tentativas < MAXIMO_DE_TENTATIVAS) {
            status = Status.PENDENTE;
            proximaTentativa = agora.plusSeconds(ESPERA_EM_SEGUNDOS[Math.min(tentativas - 1, ESPERA_EM_SEGUNDOS.length - 1)]);
        } else {
            status = Status.FALHOU;
            proximaTentativa = null;
        }
    }

    public void reenviar(Instant agora) {
        status = Status.PENDENTE;
        tentativas = 0;
        proximaTentativa = agora;
    }

    public boolean pertenceA(UUID empresa) {
        return clienteId.equals(empresa);
    }

    private static String limitar(String texto) {
        if (texto == null) {
            return null;
        }
        String limpo = texto.replaceAll("\\s+", " ").strip();
        return limpo.length() > 500 ? limpo.substring(0, 500) : limpo;
    }

    public UUID id() { return id; }
    public UUID clienteId() { return clienteId; }
    public String evento() { return evento; }
    public String payload() { return payload; }
    public Instant criadoEm() { return criadoEm; }
    public Status status() { return status; }
    public int tentativas() { return tentativas; }
    public Integer httpStatus() { return httpStatus; }
    public String resposta() { return resposta; }
    public Instant enviadoEm() { return enviadoEm; }
    public Instant proximaTentativa() { return proximaTentativa; }
}
