package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.cliente.Operador;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperadorRepositorio {

    void salvar(Operador operador);

    Optional<Operador> buscarPorId(UUID id);

    Optional<Operador> buscarPorEmail(String email);

    List<Operador> listarPorCliente(UUID clienteId);

    boolean existeComEmail(String email);
}
