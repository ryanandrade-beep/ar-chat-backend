package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.canal.InstanciaWhatsApp;

import java.util.Optional;
import java.util.UUID;

public interface InstanciaWhatsAppRepositorio {

    void salvar(InstanciaWhatsApp instancia);

    Optional<InstanciaWhatsApp> buscarPorCliente(UUID clienteId);

    Optional<InstanciaWhatsApp> buscarPorInstancia(String instancia);

    void remover(UUID clienteId);
}
