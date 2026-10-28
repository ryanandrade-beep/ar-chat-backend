package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.plano.Plano;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanoRepositorio {

    void salvar(Plano plano);

    Optional<Plano> buscarPorId(UUID id);

    List<Plano> listarTodos();

    boolean existeComNome(String nome, UUID exceto);
}
