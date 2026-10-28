package br.com.archat.infraestrutura.persistencia.jpa;

import br.com.archat.infraestrutura.persistencia.entidade.InstanciaWhatsAppEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstanciaWhatsAppJpa extends JpaRepository<InstanciaWhatsAppEntity, UUID> {

    Optional<InstanciaWhatsAppEntity> findByInstancia(String instancia);
}
