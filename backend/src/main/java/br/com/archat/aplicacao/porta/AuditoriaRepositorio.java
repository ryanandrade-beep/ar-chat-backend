package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.auditoria.RegistroDeAuditoria;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AuditoriaRepositorio {
    void salvar(RegistroDeAuditoria registro);

    List<RegistroDeAuditoria> listar(UUID clienteId, Instant de, Instant ate, UUID autorId, String acao, String busca, int limite);
}
