package br.com.archat.infraestrutura.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "administrador")
public class AdministradorEntity {

    @Id
    private UUID id;
    private String nome;
    private String login;
    @Column(name = "senha_hash")
    private String senhaHash;

    private String email;
    private String telefone;

    protected AdministradorEntity() {
    }

    public AdministradorEntity(UUID id, String nome, String login, String senhaHash) {
        this.id = id;
        this.nome = nome;
        this.login = login;
        this.senhaHash = senhaHash;
    }

    public void definirContato(String email, String telefone) {
        this.email = email;
        this.telefone = telefone;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public String getLogin() { return login; }
    public String getSenhaHash() { return senhaHash; }
    public String getEmail() { return email; }
    public String getTelefone() { return telefone; }
}
