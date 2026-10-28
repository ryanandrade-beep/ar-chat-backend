package br.com.archat.infraestrutura.persistencia.jpa;

import br.com.archat.infraestrutura.persistencia.entidade.OperadorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OperadorJpa extends JpaRepository<OperadorEntity, UUID> {

    Optional<OperadorEntity> findByEmail(String email);

    List<OperadorEntity> findByClienteId(UUID clienteId);

    boolean existsByEmail(String email);
}
