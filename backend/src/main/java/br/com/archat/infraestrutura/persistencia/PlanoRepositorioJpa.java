package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.AssinaturaRepositorio;
import br.com.archat.aplicacao.porta.PlanoRepositorio;
import br.com.archat.dominio.plano.Assinatura;
import br.com.archat.dominio.plano.Plano;
import br.com.archat.infraestrutura.persistencia.entidade.AssinaturaEntity;
import br.com.archat.infraestrutura.persistencia.entidade.PlanoEntity;
import br.com.archat.infraestrutura.persistencia.jpa.AssinaturaJpa;
import br.com.archat.infraestrutura.persistencia.jpa.PlanoJpa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Planos e assinaturas ficam juntos porque um não existe sem o outro no backoffice. */
@Repository
class PlanoRepositorioJpa implements PlanoRepositorio, AssinaturaRepositorio {

    private final PlanoJpa planos;
    private final AssinaturaJpa assinaturas;

    PlanoRepositorioJpa(PlanoJpa planos, AssinaturaJpa assinaturas) {
        this.planos = planos;
        this.assinaturas = assinaturas;
    }

    @Override
    public void salvar(Plano p) {
        planos.save(new PlanoEntity(p.id(), p.nome(), p.mensalidade(), p.operadoresIncluidos(), p.precoNormal(),
                p.precoUrgente(), p.ativo(), p.criadoEm()));
    }

    @Override
    public Optional<Plano> buscarPorId(UUID id) {
        return planos.findById(id).map(PlanoRepositorioJpa::paraDominio);
    }

    @Override
    public List<Plano> listarTodos() {
        return planos.findAll().stream().map(PlanoRepositorioJpa::paraDominio).toList();
    }

    @Override
    public boolean existeComNome(String nome, UUID exceto) {
        return exceto == null ? planos.existsByNomeIgnoreCase(nome) : planos.existsByNomeIgnoreCaseAndIdNot(nome, exceto);
    }

    @Override
    public void salvar(Assinatura a) {
        assinaturas.save(new AssinaturaEntity(a.clienteId(), a.planoId(), a.situacao(), a.inicio(), a.ultimaCobranca()));
    }

    @Override
    public Optional<Assinatura> buscarPorCliente(UUID clienteId) {
        return assinaturas.findById(clienteId)
                .map(e -> new Assinatura(e.getClienteId(), e.getPlanoId(), e.getSituacao(), e.getInicio(), e.getUltimaCobranca()));
    }

    @Override
    public List<Assinatura> listarTodas() {
        return assinaturas.findAll().stream()
                .map(e -> new Assinatura(e.getClienteId(), e.getPlanoId(), e.getSituacao(), e.getInicio(), e.getUltimaCobranca()))
                .toList();
    }

    private static Plano paraDominio(PlanoEntity e) {
        return Plano.reconstituir(e.getId(), e.getNome(), e.getMensalidade(), e.getOperadoresIncluidos(),
                e.getPrecoNormal(), e.getPrecoUrgente(), e.isAtivo(), e.getCriadoEm());
    }
}
