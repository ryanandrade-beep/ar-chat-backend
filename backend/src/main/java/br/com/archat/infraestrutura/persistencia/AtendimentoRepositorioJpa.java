package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.AtendimentoRepositorio;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.atendimento.StatusAtendimento;
import br.com.archat.infraestrutura.persistencia.entidade.AtendimentoEntity;
import br.com.archat.infraestrutura.persistencia.jpa.AtendimentoJpa;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class AtendimentoRepositorioJpa implements AtendimentoRepositorio {

    private final AtendimentoJpa jpa;

    AtendimentoRepositorioJpa(AtendimentoJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Atendimento a) {
        jpa.save(new AtendimentoEntity(a.id(), a.conversaId(), a.clienteId(), a.origem(), a.status(), a.abertoEm(),
                a.primeiraRespostaEm(), a.finalizadoEm(), a.mensagensEnviadas(), a.mensagensRecebidas()));
    }

    @Override
    public Optional<Atendimento> buscarAbertoDaConversa(UUID conversaId) {
        return jpa.findFirstByConversaIdAndStatusNotOrderByAbertoEmDesc(conversaId, StatusAtendimento.FINALIZADO)
                .map(AtendimentoRepositorioJpa::paraDominio);
    }

    @Override
    public List<Atendimento> listarFinalizados(UUID clienteId, Instant de, Instant ate) {
        List<AtendimentoEntity> linhas = clienteId == null
                ? jpa.findByStatusAndFinalizadoEmGreaterThanEqualAndFinalizadoEmLessThanOrderByFinalizadoEmDesc(
                        StatusAtendimento.FINALIZADO, de, ate)
                : jpa.findByClienteIdAndStatusAndFinalizadoEmGreaterThanEqualAndFinalizadoEmLessThanOrderByFinalizadoEmDesc(
                        clienteId, StatusAtendimento.FINALIZADO, de, ate);
        return linhas.stream().map(AtendimentoRepositorioJpa::paraDominio).toList();
    }

    @Override
    public List<Atendimento> listarAbertosNoPeriodo(UUID clienteId, Instant de, Instant ate) {
        return jpa.findByClienteIdAndAbertoEmGreaterThanEqualAndAbertoEmLessThan(clienteId, de, ate).stream()
                .map(AtendimentoRepositorioJpa::paraDominio).toList();
    }

    private static Atendimento paraDominio(AtendimentoEntity e) {
        return Atendimento.reconstituir(e.getId(), e.getConversaId(), e.getClienteId(), e.getOrigem(), e.getStatus(),
                e.getAbertoEm(), e.getPrimeiraRespostaEm(), e.getFinalizadoEm(), e.getMensagensEnviadas(),
                e.getMensagensRecebidas());
    }
}
