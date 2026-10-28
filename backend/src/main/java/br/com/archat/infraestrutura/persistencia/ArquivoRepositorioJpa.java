package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.ArquivoRepositorio;
import br.com.archat.dominio.arquivo.Arquivo;
import br.com.archat.infraestrutura.persistencia.entidade.ArquivoEntity;
import br.com.archat.infraestrutura.persistencia.jpa.ArquivoJpa;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Repository
class ArquivoRepositorioJpa implements ArquivoRepositorio {

    private final ArquivoJpa jpa;

    ArquivoRepositorioJpa(ArquivoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Arquivo a) {
        jpa.save(new ArquivoEntity(a.id(), a.clienteId(), a.nome(), a.tipoMime(), a.conteudo(), a.criadoEm()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Arquivo> buscarPorId(UUID id) {
        return jpa.findById(id).map(e -> new Arquivo(e.getId(), e.getClienteId(), e.getNome(), e.getTipoMime(),
                e.getConteudo(), e.getCriadoEm()));
    }
}
