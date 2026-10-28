package br.com.archat.infraestrutura.persistencia.jpa;

import br.com.archat.infraestrutura.persistencia.entidade.DepartamentoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DepartamentoJpa extends JpaRepository<DepartamentoEntity, UUID> {

    List<DepartamentoEntity> findByClienteId(UUID clienteId);

    boolean existsByClienteIdAndNomeIgnoreCase(UUID clienteId, String nome);

    boolean existsByClienteIdAndNomeIgnoreCaseAndIdNot(UUID clienteId, String nome, UUID id);
}
