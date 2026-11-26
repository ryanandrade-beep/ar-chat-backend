package br.com.archat.dominio.auditoria;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Uma linha da trilha de auditoria. O nome do autor é guardado como estava na hora, mesmo que o operador mude depois. */
public record RegistroDeAuditoria(UUID id, UUID clienteId, UUID autorId, String autorNome, String acao, String recurso,
                                  String detalhe, Instant criadaEm) {

    public RegistroDeAuditoria {
        Objects.requireNonNull(id);
        Objects.requireNonNull(clienteId);
        Objects.requireNonNull(acao);
        Objects.requireNonNull(criadaEm);
        autorNome = limitar(autorNome == null || autorNome.isBlank() ? "Sistema" : autorNome, 120);
        recurso = limitar(recurso, 200);
        detalhe = limitar(detalhe, 500);
    }

    public static RegistroDeAuditoria novo(UUID clienteId, UUID autorId, String autorNome, AcaoDeAuditoria acao,
                                           String recurso, String detalhe, Instant agora) {
        return new RegistroDeAuditoria(UUID.randomUUID(), clienteId, autorId, autorNome, acao.codigo(), recurso, detalhe, agora);
    }

    private static String limitar(String texto, int maximo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        String limpo = texto.strip();
        return limpo.length() > maximo ? limpo.substring(0, maximo) : limpo;
    }
}
