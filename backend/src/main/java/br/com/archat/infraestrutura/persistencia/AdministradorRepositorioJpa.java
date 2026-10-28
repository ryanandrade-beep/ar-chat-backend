package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.Administrador;
import br.com.archat.aplicacao.porta.AdministradorRepositorio;
import br.com.archat.infraestrutura.persistencia.entidade.AdministradorEntity;
import br.com.archat.infraestrutura.persistencia.jpa.AdministradorJpa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AdministradorRepositorioJpa implements AdministradorRepositorio {

    private final AdministradorJpa jpa;

    AdministradorRepositorioJpa(AdministradorJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Administrador a) {
        AdministradorEntity entidade = new AdministradorEntity(a.id(), a.nome(), a.login(), a.senhaHash());
        entidade.definirContato(a.email(), a.telefone());
        jpa.save(entidade);
    }

    @Override
    public Optional<Administrador> buscarPorId(UUID id) {
        return jpa.findById(id).map(AdministradorRepositorioJpa::paraDominio);
    }

    @Override
    public Optional<Administrador> buscarPorLogin(String login) {
        return login == null ? Optional.empty()
                : jpa.findByLogin(login.strip().toLowerCase()).map(AdministradorRepositorioJpa::paraDominio);
    }

    @Override
    public List<Administrador> listarTodos() {
        return jpa.findAllByOrderByNomeAsc().stream().map(AdministradorRepositorioJpa::paraDominio).toList();
    }

    @Override
    public void excluir(UUID id) {
        jpa.deleteById(id);
    }

    @Override
    public long contar() {
        return jpa.count();
    }

    private static Administrador paraDominio(AdministradorEntity e) {
        return new Administrador(e.getId(), e.getNome(), e.getLogin(), e.getSenhaHash(), e.getEmail(), e.getTelefone());
    }
}
