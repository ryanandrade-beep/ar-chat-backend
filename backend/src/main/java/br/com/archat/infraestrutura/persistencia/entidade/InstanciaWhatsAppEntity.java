package br.com.archat.infraestrutura.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instancia_whatsapp")
public class InstanciaWhatsAppEntity {

    @Id
    @Column(name = "cliente_id")
    private UUID clienteId;
    private String instancia;
    private String token;
    @Column(name = "vinculada_em")
    private Instant vinculadaEm;

    protected InstanciaWhatsAppEntity() {
    }

    public InstanciaWhatsAppEntity(UUID clienteId, String instancia, String token, Instant vinculadaEm) {
        this.clienteId = clienteId;
        this.instancia = instancia;
        this.token = token;
        this.vinculadaEm = vinculadaEm;
    }

    public UUID getClienteId() { return clienteId; }
    public String getInstancia() { return instancia; }
    public String getToken() { return token; }
    public Instant getVinculadaEm() { return vinculadaEm; }
}
