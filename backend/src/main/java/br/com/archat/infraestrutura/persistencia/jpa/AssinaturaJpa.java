package br.com.archat.infraestrutura.persistencia.jpa;

import br.com.archat.infraestrutura.persistencia.entidade.AssinaturaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AssinaturaJpa extends JpaRepository<AssinaturaEntity, UUID> {
}
