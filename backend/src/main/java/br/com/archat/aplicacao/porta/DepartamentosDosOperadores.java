package br.com.archat.aplicacao.porta;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Em quais departamentos cada operador atua. */
public interface DepartamentosDosOperadores {

    Map<UUID, List<UUID>> doCliente(UUID clienteId);

    void definir(UUID operadorId, List<UUID> departamentoIds);

    void definirOperadoresDoDepartamento(UUID departamentoId, List<UUID> operadorIds);
}
