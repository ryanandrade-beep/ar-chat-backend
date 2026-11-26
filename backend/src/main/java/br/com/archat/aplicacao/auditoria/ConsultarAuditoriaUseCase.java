package br.com.archat.aplicacao.auditoria;

import br.com.archat.aplicacao.porta.AuditoriaRepositorio;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.dominio.auditoria.RegistroDeAuditoria;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Consulta da trilha de auditoria pelo suporte (backoffice): de uma empresa ou de todas. */
public class ConsultarAuditoriaUseCase {
    public static final int LIMITE_PADRAO = 200;
    public static final int LIMITE_MAXIMO = 1000;

    public record Entrada(RegistroDeAuditoria registro, String empresa) {
    }

    private final AuditoriaRepositorio auditoria;
    private final ClienteRepositorio clientes;

    public ConsultarAuditoriaUseCase(AuditoriaRepositorio auditoria, ClienteRepositorio clientes) {
        this.auditoria = auditoria;
        this.clientes = clientes;
    }

    public List<Entrada> listar(UUID clienteId, Instant de, Instant ate, UUID autorId, String acao, String busca, Integer limite) {
        int tamanho = limite == null ? LIMITE_PADRAO : Math.max(1, Math.min(limite, LIMITE_MAXIMO));
        Map<UUID, String> nomes = new HashMap<>();
        return auditoria.listar(clienteId, de, ate, autorId, acao == null || acao.isBlank() ? null : acao,
                        busca == null || busca.isBlank() ? null : busca.strip(), tamanho).stream()
                .map(r -> new Entrada(r, nomes.computeIfAbsent(r.clienteId(),
                        id -> clientes.buscarPorId(id).map(c -> c.nome()).orElse("—"))))
                .toList();
    }
}
