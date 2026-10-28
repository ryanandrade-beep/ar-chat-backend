package br.com.archat.infraestrutura.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "departamento")
public class DepartamentoEntity {

    @Id
    private UUID id;
    @Column(name = "cliente_id")
    private UUID clienteId;
    private String nome;
    private boolean ativo;
    @Column(name = "criado_em")
    private Instant criadoEm;

    protected DepartamentoEntity() {
    }

    public DepartamentoEntity(UUID id, UUID clienteId, String nome, boolean ativo, Instant criadoEm) {
        this.id = id;
        this.clienteId = clienteId;
        this.nome = nome;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public String getNome() { return nome; }
    public boolean isAtivo() { return ativo; }
    public Instant getCriadoEm() { return criadoEm; }
}
