package br.com.archat.infraestrutura.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "plano")
public class PlanoEntity {

    @Id
    private UUID id;
    private String nome;
    private BigDecimal mensalidade;
    @Column(name = "operadores_incluidos")
    private int operadoresIncluidos;
    @Column(name = "preco_normal")
    private BigDecimal precoNormal;
    @Column(name = "preco_urgente")
    private BigDecimal precoUrgente;
    private boolean ativo;
    @Column(name = "criado_em")
    private Instant criadoEm;

    protected PlanoEntity() {
    }

    public PlanoEntity(UUID id, String nome, BigDecimal mensalidade, int operadoresIncluidos, BigDecimal precoNormal,
                       BigDecimal precoUrgente, boolean ativo, Instant criadoEm) {
        this.id = id;
        this.nome = nome;
        this.mensalidade = mensalidade;
        this.operadoresIncluidos = operadoresIncluidos;
        this.precoNormal = precoNormal;
        this.precoUrgente = precoUrgente;
        this.ativo = ativo;
        this.criadoEm = criadoEm;
    }

    public UUID getId() { return id; }
    public String getNome() { return nome; }
    public BigDecimal getMensalidade() { return mensalidade; }
    public int getOperadoresIncluidos() { return operadoresIncluidos; }
    public BigDecimal getPrecoNormal() { return precoNormal; }
    public BigDecimal getPrecoUrgente() { return precoUrgente; }
    public boolean isAtivo() { return ativo; }
    public Instant getCriadoEm() { return criadoEm; }
}
