package br.com.archat.aplicacao.operador;

import br.com.archat.aplicacao.departamento.GerenciarDepartamentosUseCase;
import br.com.archat.aplicacao.porta.DepartamentosDosOperadores;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.departamento.Departamento;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Departamentos em que cada operador atua. Só valem departamentos da própria empresa; o perfil do responsável
 * (dono) não é vinculado a departamento.
 */
public class GerenciarDepartamentosDoOperadorUseCase {
    private final DepartamentosDosOperadores vinculos;
    private final GerenciarOperadoresUseCase operadores;
    private final GerenciarDepartamentosUseCase departamentos;
    private final UnidadeDeTrabalho unidadeDeTrabalho;

    public GerenciarDepartamentosDoOperadorUseCase(DepartamentosDosOperadores vinculos, GerenciarOperadoresUseCase operadores,
                                                   GerenciarDepartamentosUseCase departamentos,
                                                   UnidadeDeTrabalho unidadeDeTrabalho) {
        this.vinculos = vinculos;
        this.operadores = operadores;
        this.departamentos = departamentos;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
    }

    public Map<UUID, List<UUID>> doCliente(UUID clienteId) {
        return vinculos.doCliente(clienteId);
    }

    public List<UUID> definirOperadores(UUID clienteId, UUID departamentoId, List<UUID> operadorIds) {
        departamentos.buscar(clienteId, departamentoId);
        Set<UUID> pedidos = new LinkedHashSet<>(operadorIds == null ? List.<UUID>of() : operadorIds);
        Set<UUID> daEmpresa = operadores.listar(clienteId).stream().filter(o -> !o.dono()).map(o -> o.id())
                .collect(Collectors.toSet());
        if (!daEmpresa.containsAll(pedidos)) {
            throw new RegraDeNegocioException("Operador inválido para esta empresa");
        }
        List<UUID> lista = List.copyOf(pedidos);
        unidadeDeTrabalho.executar(() -> vinculos.definirOperadoresDoDepartamento(departamentoId, lista));
        return lista;
    }

    public List<UUID> definir(UUID clienteId, UUID operadorId, List<UUID> ids) {
        if (operadores.buscar(clienteId, operadorId).dono()) {
            throw new RegraDeNegocioException("O perfil do responsável não é vinculado a departamento");
        }
        Set<UUID> pedidos = new LinkedHashSet<>(ids == null ? List.<UUID>of() : ids);
        Set<UUID> daEmpresa = departamentos.listar(clienteId).stream().map(Departamento::id).collect(Collectors.toSet());
        if (!daEmpresa.containsAll(pedidos)) {
            throw new RegraDeNegocioException("Departamento inválido para esta empresa");
        }
        List<UUID> lista = List.copyOf(pedidos);
        unidadeDeTrabalho.executar(() -> vinculos.definir(operadorId, lista));
        return lista;
    }
}
