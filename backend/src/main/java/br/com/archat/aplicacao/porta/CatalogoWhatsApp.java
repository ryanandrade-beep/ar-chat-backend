package br.com.archat.aplicacao.porta;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Catálogo, pedido e status de pedido do WhatsApp Business da empresa. */
public interface CatalogoWhatsApp {
    PaginaDoCatalogo listar(UUID clienteId, String cursor);

    void enviarPedido(UUID clienteId, String telefone, PedidoParaEnvio pedido);

    void atualizarStatusDoPedido(UUID clienteId, String telefone, AtualizacaoDePedido atualizacao);

    record Produto(String id, String nome, String descricao, BigDecimal preco, BigDecimal precoPromocional, String moeda,
                   String imagem, String disponibilidade, boolean oculto, String codigoInterno) {
    }

    record PaginaDoCatalogo(boolean carrinhoAtivo, List<Produto> produtos, String proximoCursor) {
    }

    record ItemDoPedido(String produtoId, String nome, int quantidade, BigDecimal valor) {
    }

    record PedidoParaEnvio(List<ItemDoPedido> itens, BigDecimal frete, String chavePix, String tipoDaChavePix,
                           String nomeDaChavePix, boolean aceitaCartao) {
    }

    record AtualizacaoDePedido(String mensagemId, String referencia, String requisicao, String statusDoPedido,
                               String statusDoPagamento, List<ItemDoPedido> itens, BigDecimal frete, String mensagem) {
    }
}
