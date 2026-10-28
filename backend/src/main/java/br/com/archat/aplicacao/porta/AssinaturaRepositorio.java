package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.plano.Assinatura;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssinaturaRepositorio {

    void salvar(Assinatura assinatura);

    Optional<Assinatura> buscarPorCliente(UUID clienteId);

    List<Assinatura> listarTodas();
}
