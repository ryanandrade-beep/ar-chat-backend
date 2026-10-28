package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.infraestrutura.persistencia.entidade.OperadorEntity;
import br.com.archat.infraestrutura.persistencia.jpa.OperadorJpa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class OperadorRepositorioJpa implements OperadorRepositorio {

    private final OperadorJpa jpa;

    OperadorRepositorioJpa(OperadorJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Operador o) {
        jpa.save(new OperadorEntity(o.id(), o.clienteId(), o.nome(), o.email(), o.senhaHash(), o.ativo(), o.criadoEm()));
    }

    @Override
    public Optional<Operador> buscarPorId(UUID id) {
        return jpa.findById(id).map(OperadorRepositorioJpa::paraDominio);
    }

    @Override
    public Optional<Operador> buscarPorEmail(String email) {
        return jpa.findByEmail(email).map(OperadorRepositorioJpa::paraDominio);
    }

    @Override
    public List<Operador> listarPorCliente(UUID clienteId) {
        return jpa.findByClienteId(clienteId).stream().map(OperadorRepositorioJpa::paraDominio).toList();
    }

    @Override
    public boolean existeComEmail(String email) {
        return jpa.existsByEmail(email);
    }

    private static Operador paraDominio(OperadorEntity e) {
        return Operador.reconstituir(e.getId(), e.getClienteId(), e.getNome(), e.getEmail(), e.getSenhaHash(),
                e.isAtivo(), e.getCriadoEm());
    }
}
