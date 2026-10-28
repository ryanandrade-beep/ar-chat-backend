package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.InstanciaWhatsAppRepositorio;
import br.com.archat.dominio.canal.InstanciaWhatsApp;
import br.com.archat.infraestrutura.persistencia.entidade.InstanciaWhatsAppEntity;
import br.com.archat.infraestrutura.persistencia.jpa.InstanciaWhatsAppJpa;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class InstanciaWhatsAppRepositorioJpa implements InstanciaWhatsAppRepositorio {

    private final InstanciaWhatsAppJpa jpa;

    InstanciaWhatsAppRepositorioJpa(InstanciaWhatsAppJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(InstanciaWhatsApp i) {
        jpa.save(new InstanciaWhatsAppEntity(i.clienteId(), i.instancia(), i.token(), i.vinculadaEm()));
    }

    @Override
    public Optional<InstanciaWhatsApp> buscarPorCliente(UUID clienteId) {
        return jpa.findById(clienteId).map(InstanciaWhatsAppRepositorioJpa::paraDominio);
    }

    @Override
    public Optional<InstanciaWhatsApp> buscarPorInstancia(String instancia) {
        return instancia == null ? Optional.empty() : jpa.findByInstancia(instancia).map(InstanciaWhatsAppRepositorioJpa::paraDominio);
    }

    @Override
    @Transactional
    public void remover(UUID clienteId) {
        jpa.deleteById(clienteId);
    }

    private static InstanciaWhatsApp paraDominio(InstanciaWhatsAppEntity e) {
        return new InstanciaWhatsApp(e.getClienteId(), e.getInstancia(), e.getToken(), e.getVinculadaEm());
    }
}
