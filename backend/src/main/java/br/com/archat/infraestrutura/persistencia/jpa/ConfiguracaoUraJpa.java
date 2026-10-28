package br.com.archat.infraestrutura.persistencia.jpa;

import br.com.archat.infraestrutura.persistencia.entidade.ConfiguracaoUraEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ConfiguracaoUraJpa extends JpaRepository<ConfiguracaoUraEntity, UUID> {
}
