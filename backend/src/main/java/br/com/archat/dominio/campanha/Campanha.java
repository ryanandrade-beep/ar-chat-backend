package br.com.archat.dominio.campanha;

import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Midia;
import br.com.archat.dominio.mensagem.Prioridade;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Disparo de uma mesma mensagem para vários destinatários de um cliente. A mensagem pode levar uma mídia
 * (ex.: o encarte de ofertas em imagem); nesse caso o conteúdo é a legenda.
 */
public record Campanha(UUID id, UUID clienteId, String nome, String conteudo, Midia midia, Prioridade prioridade,
                       int totalDestinatarios, BigDecimal custoTotal, Instant criadaEm) {

    public static final int MAXIMO_DESTINATARIOS = 1000;

    public Campanha {
        Objects.requireNonNull(id);
        Objects.requireNonNull(clienteId);
        Objects.requireNonNull(criadaEm);
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("O nome da campanha é obrigatório");
        }
        if (nome.strip().length() > 120) {
            throw new RegraDeNegocioException("O nome da campanha deve ter no máximo 120 caracteres");
        }
        if (midia == null && (conteudo == null || conteudo.isBlank())) {
            throw new RegraDeNegocioException("O conteúdo da campanha é obrigatório");
        }
        Mensagem.validarConteudo(conteudo, midia);
        if (prioridade == null) {
            throw new RegraDeNegocioException("A prioridade é obrigatória");
        }
        if (totalDestinatarios < 1) {
            throw new RegraDeNegocioException("A campanha precisa de ao menos um destinatário que aceite receber ofertas");
        }
        if (totalDestinatarios > MAXIMO_DESTINATARIOS) {
            throw new RegraDeNegocioException("A campanha pode ter no máximo " + MAXIMO_DESTINATARIOS + " destinatários");
        }
        nome = nome.strip();
        conteudo = conteudo == null ? "" : conteudo.strip();
    }

    public static Campanha nova(UUID clienteId, String nome, String conteudo, Prioridade prioridade,
                                int totalDestinatarios, Instant agora) {
        return nova(clienteId, nome, conteudo, null, prioridade, totalDestinatarios, agora);
    }

    public static Campanha nova(UUID clienteId, String nome, String conteudo, Midia midia, Prioridade prioridade,
                                int totalDestinatarios, Instant agora) {
        return nova(clienteId, nome, conteudo, midia, prioridade, prioridade == null ? null : prioridade.custo(),
                totalDestinatarios, agora);
    }

    public static Campanha nova(UUID clienteId, String nome, String conteudo, Midia midia, Prioridade prioridade,
                                BigDecimal precoUnitario, int totalDestinatarios, Instant agora) {
        BigDecimal custoTotal = prioridade == null || precoUnitario == null ? BigDecimal.ZERO
                : precoUnitario.multiply(BigDecimal.valueOf(totalDestinatarios));
        return new Campanha(UUID.randomUUID(), clienteId, nome, conteudo, midia, prioridade, totalDestinatarios, custoTotal, agora);
    }

    public boolean pertenceA(UUID clienteId) {
        return this.clienteId.equals(clienteId);
    }
}
