package br.com.archat.infraestrutura.persistencia.jpa;

import br.com.archat.dominio.atendimento.StatusAtendimento;
import br.com.archat.infraestrutura.persistencia.entidade.AtendimentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtendimentoJpa extends JpaRepository<AtendimentoEntity, UUID> {

    Optional<AtendimentoEntity> findFirstByConversaIdAndStatusNotOrderByAbertoEmDesc(UUID conversaId,
                                                                                    StatusAtendimento status);

    List<AtendimentoEntity> findByClienteIdAndAbertoEmGreaterThanEqualAndAbertoEmLessThan(UUID clienteId, Instant de,
                                                                                         Instant ate);

    List<AtendimentoEntity> findByStatusAndFinalizadoEmGreaterThanEqualAndFinalizadoEmLessThanOrderByFinalizadoEmDesc(
            StatusAtendimento status, Instant de, Instant ate);

    List<AtendimentoEntity> findByClienteIdAndStatusAndFinalizadoEmGreaterThanEqualAndFinalizadoEmLessThanOrderByFinalizadoEmDesc(
            UUID clienteId, StatusAtendimento status, Instant de, Instant ate);
}
