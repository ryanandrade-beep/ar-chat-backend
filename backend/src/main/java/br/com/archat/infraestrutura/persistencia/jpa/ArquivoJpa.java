package br.com.archat.infraestrutura.persistencia.jpa;

import br.com.archat.infraestrutura.persistencia.entidade.ArquivoEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArquivoJpa extends JpaRepository<ArquivoEntity, UUID> {
}
