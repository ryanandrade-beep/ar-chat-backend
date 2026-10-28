package br.com.archat.infraestrutura.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "arquivo")
public class ArquivoEntity {

    @Id
    private UUID id;
    @Column(name = "cliente_id")
    private UUID clienteId;
    private String nome;
    @Column(name = "tipo_mime")
    private String tipoMime;
    private int tamanho;
    @JdbcTypeCode(SqlTypes.VARBINARY)
    private byte[] conteudo;
    @Column(name = "criado_em")
    private Instant criadoEm;

    protected ArquivoEntity() {
    }

    public ArquivoEntity(UUID id, UUID clienteId, String nome, String tipoMime, byte[] conteudo, Instant criadoEm) {
        this.id = id;
        this.clienteId = clienteId;
        this.nome = nome;
        this.tipoMime = tipoMime;
        this.tamanho = conteudo.length;
        this.conteudo = conteudo;
        this.criadoEm = criadoEm;
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public String getNome() { return nome; }
    public String getTipoMime() { return tipoMime; }
    public byte[] getConteudo() { return conteudo; }
    public Instant getCriadoEm() { return criadoEm; }
}
