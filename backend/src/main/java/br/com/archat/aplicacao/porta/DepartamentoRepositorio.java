package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.departamento.ConfiguracaoUra;
import br.com.archat.dominio.departamento.Departamento;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Departamentos e a configuração da URA de cada workspace. */
public interface DepartamentoRepositorio {

    void salvar(Departamento departamento);

    Optional<Departamento> buscarPorId(UUID id);

    List<Departamento> listarPorCliente(UUID clienteId);

    boolean existeComNome(UUID clienteId, String nome, UUID exceto);

    void salvar(ConfiguracaoUra configuracao);

    Optional<ConfiguracaoUra> buscarConfiguracao(UUID clienteId);
}
