package br.com.archat.dominio.plano;

import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Prioridade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Plano comercial criado pelo backoffice: mensalidade, quantidade de operadores incluída e preço de cada
 * mensagem (normal e urgente). A cobrança continua por mensagem; a mensalidade é cobrada uma vez por mês.
 */
public class Plano {

    private final UUID id;
    private String nome;
    private BigDecimal mensalidade;
    private int operadoresIncluidos;
    private BigDecimal precoNormal;
    private BigDecimal precoUrgente;
    private boolean ativo;
    private final Instant criadoEm;

    private Plano(UUID id, String nome, BigDecimal mensalidade, int operadoresIncluidos, BigDecimal precoNormal,
                  BigDecimal precoUrgente, boolean ativo, Instant criadoEm) {
        this.id = Objects.requireNonNull(id);
        this.criadoEm = Objects.requireNonNull(criadoEm);
        definir(nome, mensalidade, operadoresIncluidos, precoNormal, precoUrgente, ativo);
    }

    public static Plano novo(String nome, BigDecimal mensalidade, int operadoresIncluidos, BigDecimal precoNormal,
                             BigDecimal precoUrgente, Instant agora) {
        return new Plano(UUID.randomUUID(), nome, mensalidade, operadoresIncluidos, precoNormal, precoUrgente, true, agora);
    }

    public static Plano reconstituir(UUID id, String nome, BigDecimal mensalidade, int operadoresIncluidos,
                                     BigDecimal precoNormal, BigDecimal precoUrgente, boolean ativo, Instant criadoEm) {
        return new Plano(id, nome, mensalidade, operadoresIncluidos, precoNormal, precoUrgente, ativo, criadoEm);
    }

    public void alterar(String nome, BigDecimal mensalidade, int operadoresIncluidos, BigDecimal precoNormal,
                        BigDecimal precoUrgente, boolean ativo) {
        definir(nome, mensalidade, operadoresIncluidos, precoNormal, precoUrgente, ativo);
    }

    private void definir(String nome, BigDecimal mensalidade, int operadoresIncluidos, BigDecimal precoNormal,
                         BigDecimal precoUrgente, boolean ativo) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 60) {
            throw new RegraDeNegocioException("Informe o nome do plano (até 60 caracteres)");
        }
        if (mensalidade == null || mensalidade.signum() < 0) {
            throw new RegraDeNegocioException("A mensalidade não pode ser negativa");
        }
        if (operadoresIncluidos < 1 || operadoresIncluidos > 1000) {
            throw new RegraDeNegocioException("O plano deve incluir de 1 a 1.000 operadores");
        }
        if (precoNormal == null || precoNormal.signum() <= 0 || precoUrgente == null || precoUrgente.signum() <= 0) {
            throw new RegraDeNegocioException("Os preços da mensagem devem ser maiores que zero");
        }
        if (precoUrgente.compareTo(precoNormal) < 0) {
            throw new RegraDeNegocioException("A mensagem urgente não pode custar menos que a normal");
        }
        this.nome = nome.strip();
        this.mensalidade = mensalidade.setScale(2, RoundingMode.HALF_EVEN);
        this.operadoresIncluidos = operadoresIncluidos;
        this.precoNormal = precoNormal.setScale(2, RoundingMode.HALF_EVEN);
        this.precoUrgente = precoUrgente.setScale(2, RoundingMode.HALF_EVEN);
        this.ativo = ativo;
    }

    public BigDecimal custo(Prioridade prioridade) {
        return prioridade == Prioridade.URGENTE ? precoUrgente : precoNormal;
    }

    public UUID id() { return id; }
    public String nome() { return nome; }
    public BigDecimal mensalidade() { return mensalidade; }
    public int operadoresIncluidos() { return operadoresIncluidos; }
    public BigDecimal precoNormal() { return precoNormal; }
    public BigDecimal precoUrgente() { return precoUrgente; }
    public boolean ativo() { return ativo; }
    public Instant criadoEm() { return criadoEm; }
}
