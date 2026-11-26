package br.com.archat.dominio.entrega;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Um produto do pedido (normalmente vindo do catálogo do WhatsApp Business). */
public record ItemDeEntrega(String produtoId, String nome, int quantidade, BigDecimal valorUnitario, String imagem) {

    public ItemDeEntrega(String produtoId, String nome, int quantidade, BigDecimal valorUnitario) {
        this(produtoId, nome, quantidade, valorUnitario, null);
    }

    public ItemDeEntrega {
        nome = nome == null ? "" : nome.strip();
        if (nome.isEmpty() || nome.length() > 200) {
            throw new RegraDeNegocioException("O item do pedido precisa de um nome (até 200 caracteres)");
        }
        if (quantidade < 1 || quantidade > 999) {
            throw new RegraDeNegocioException("A quantidade de cada item deve ficar entre 1 e 999");
        }
        if (valorUnitario == null || valorUnitario.signum() < 0 || valorUnitario.compareTo(new BigDecimal("99999.99")) > 0) {
            throw new RegraDeNegocioException("O valor de cada item deve ficar entre R$ 0,00 e R$ 99.999,99");
        }
        valorUnitario = valorUnitario.setScale(2, RoundingMode.HALF_EVEN);
        imagem = imagem == null || imagem.isBlank() || imagem.length() > 1000 ? null : imagem.strip();
    }

    public BigDecimal subtotal() {
        return valorUnitario.multiply(BigDecimal.valueOf(quantidade));
    }
}
