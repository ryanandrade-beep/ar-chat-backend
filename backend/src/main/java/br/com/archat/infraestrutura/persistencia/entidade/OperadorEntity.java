package br.com.archat.infraestrutura.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "operador")
public class OperadorEntity {

    @Id
    private UUID id;
    @Column(name = "cliente_id")
    private UUID clienteId;
    private String nome;
    private String email;
    @Column(name = "senha_hash")
    private String senhaHash;
    private boolean ativo;
    @Column(name = "criado_em")
    private Instant criadoEm;

    private boolean dono;

    protected OperadorEntity() {
    }

    public OperadorEntity(UUID id, UUID clienteId, String nome, String email, String senhaHash, boolean ativo,
                          Instant criadoEm) {
        this.id = id;
        this.clienteId = clienteId;
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public String getNome() { return nome; }
    public String getEmail() { return email; }
    public String getSenhaHash() { return senhaHash; }
    public boolean isAtivo() { return ativo; }
    public Instant getCriadoEm() { return criadoEm; }
    public boolean isDono() { return dono; }

    public void definirDono(boolean dono) {
        this.dono = dono;
    }
}
