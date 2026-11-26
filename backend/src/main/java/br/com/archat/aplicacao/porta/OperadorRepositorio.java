package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.cliente.Operador;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperadorRepositorio {

    void salvar(Operador operador);

    Optional<Operador> buscarPorId(UUID id);

    List<Operador> listarPorEmail(String email);

    List<Operador> listarPorCliente(UUID clienteId);

    boolean existeComEmailNaEmpresa(UUID clienteId, String email);
}
