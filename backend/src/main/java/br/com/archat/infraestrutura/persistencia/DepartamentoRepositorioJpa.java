package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.DepartamentoRepositorio;
import br.com.archat.dominio.departamento.ConfiguracaoUra;
import br.com.archat.dominio.departamento.Departamento;
import br.com.archat.infraestrutura.persistencia.entidade.ConfiguracaoUraEntity;
import br.com.archat.infraestrutura.persistencia.entidade.DepartamentoEntity;
import br.com.archat.infraestrutura.persistencia.jpa.ConfiguracaoUraJpa;
import br.com.archat.infraestrutura.persistencia.jpa.DepartamentoJpa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class DepartamentoRepositorioJpa implements DepartamentoRepositorio {

    private final DepartamentoJpa departamentos;
    private final ConfiguracaoUraJpa configuracoes;

    DepartamentoRepositorioJpa(DepartamentoJpa departamentos, ConfiguracaoUraJpa configuracoes) {
        this.departamentos = departamentos;
        this.configuracoes = configuracoes;
    }

    @Override
    public void salvar(Departamento d) {
        departamentos.save(new DepartamentoEntity(d.id(), d.clienteId(), d.nome(), d.ativo(), d.criadoEm()));
    }

    @Override
    public Optional<Departamento> buscarPorId(UUID id) {
        return departamentos.findById(id).map(DepartamentoRepositorioJpa::paraDominio);
    }

    @Override
    public List<Departamento> listarPorCliente(UUID clienteId) {
        return departamentos.findByClienteId(clienteId).stream().map(DepartamentoRepositorioJpa::paraDominio).toList();
    }

    @Override
    public boolean existeComNome(UUID clienteId, String nome, UUID exceto) {
        return exceto == null ? departamentos.existsByClienteIdAndNomeIgnoreCase(clienteId, nome)
                : departamentos.existsByClienteIdAndNomeIgnoreCaseAndIdNot(clienteId, nome, exceto);
    }

    @Override
    public void salvar(ConfiguracaoUra c) {
        configuracoes.save(new ConfiguracaoUraEntity(c.clienteId(), c.ativa(), c.mensagemBoasVindas(), c.mensagemPergunta(),
                c.maximoTentativas(), c.departamentoPadraoId(), c.ignorarGrupos()));
    }

    @Override
    public Optional<ConfiguracaoUra> buscarConfiguracao(UUID clienteId) {
        return configuracoes.findById(clienteId).map(e -> new ConfiguracaoUra(e.getClienteId(), e.isAtiva(),
                e.getMensagemBoasVindas(), e.getMensagemPergunta(), e.getMaximoTentativas(), e.getDepartamentoPadraoId(),
                e.isIgnorarGrupos()));
    }

    private static Departamento paraDominio(DepartamentoEntity e) {
        return Departamento.reconstituir(e.getId(), e.getClienteId(), e.getNome(), e.isAtivo(), e.getCriadoEm());
    }
}
