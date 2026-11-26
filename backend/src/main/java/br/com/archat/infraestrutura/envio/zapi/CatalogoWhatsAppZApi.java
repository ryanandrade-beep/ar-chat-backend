package br.com.archat.infraestrutura.envio.zapi;

import br.com.archat.aplicacao.excecao.CanalIndisponivelException;
import br.com.archat.aplicacao.porta.CatalogoWhatsApp;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Catálogo e pedidos pela Z-API (somente WhatsApp Business): {@code POST /catalogs} (lista com cursor),
 * {@code POST /send-order} e {@code POST /order-status-update}. Os preços do catálogo vêm em milésimos
 * (69900 = R$ 69,90); nos envios de pedido o valor vai em reais.
 */
@Component
@ConditionalOnProperty(name = "archat.envio.provedor", havingValue = "zapi")
class CatalogoWhatsAppZApi implements CatalogoWhatsApp {

    private final ClienteZApi zapi;
    private final InstanciasZApi instancias;

    CatalogoWhatsAppZApi(ClienteZApi zapi, InstanciasZApi instancias) {
        this.zapi = zapi;
        this.instancias = instancias;
    }

    @Override
    @SuppressWarnings("unchecked")
    public PaginaDoCatalogo listar(UUID clienteId, String cursor) {
        Map<String, Object> resposta = chamar(() -> zapi.catalogo(instancias.da(clienteId), cursor));
        List<Produto> produtos = new ArrayList<>();
        Object lista = resposta == null ? null : resposta.get("products");
        if (lista instanceof List<?> itens) {
            for (Object item : itens) {
                Map<String, Object> p = (Map<String, Object>) item;
                List<String> imagens = p.get("images") instanceof List<?> l ? (List<String>) l : List.of();
                produtos.add(new Produto(texto(p.get("id")), texto(p.get("name")), texto(p.get("description")),
                        reais(p.get("price")), reais(p.get("salePrice")), textoOu(p.get("currency"), "BRL"),
                        imagens.isEmpty() ? null : imagens.get(0), texto(p.get("availability")),
                        Boolean.TRUE.equals(p.get("isHidden")), texto(p.get("retailerId"))));
            }
        }
        String proximo = resposta == null ? null : texto(resposta.get("nextCursor"));
        return new PaginaDoCatalogo(resposta != null && Boolean.TRUE.equals(resposta.get("cartEnabled")), produtos,
                proximo == null || proximo.isBlank() ? null : proximo);
    }

    @Override
    public void enviarPedido(UUID clienteId, String telefone, PedidoParaEnvio pedido) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("phone", EnvioMensagemZApi.comDdi(telefone));
        corpo.put("order", pedidoParaJson(pedido.itens(), pedido.frete()));
        Map<String, Object> pagamento = new LinkedHashMap<>();
        if (pedido.chavePix() != null) {
            Map<String, Object> pix = new LinkedHashMap<>();
            pix.put("key", pedido.chavePix());
            pix.put("keyType", pedido.tipoDaChavePix());
            if (pedido.nomeDaChavePix() != null) {
                pix.put("name", pedido.nomeDaChavePix());
            }
            pagamento.put("pix", pix);
        }
        pagamento.put("card", Map.of("enabled", pedido.aceitaCartao()));
        corpo.put("paymentSettings", pagamento);
        chamar(() -> zapi.enviarPedido(instancias.da(clienteId), corpo));
    }

    @Override
    public void atualizarStatusDoPedido(UUID clienteId, String telefone, AtualizacaoDePedido a) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("phone", EnvioMensagemZApi.comDdi(telefone));
        corpo.put("messageId", a.mensagemId());
        corpo.put("referenceId", a.referencia());
        corpo.put("orderRequestId", a.requisicao());
        corpo.put("orderStatus", a.statusDoPedido());
        corpo.put("paymentStatus", a.statusDoPagamento());
        if (a.mensagem() != null) {
            corpo.put("message", a.mensagem());
        }
        corpo.put("order", pedidoParaJson(a.itens(), a.frete()));
        chamar(() -> zapi.atualizarStatusDoPedido(instancias.da(clienteId), corpo));
    }

    private static Map<String, Object> pedidoParaJson(List<ItemDoPedido> itens, BigDecimal frete) {
        Map<String, Object> pedido = new LinkedHashMap<>();
        pedido.put("currency", "BRL");
        if (frete != null && frete.signum() > 0) {
            pedido.put("shipping", frete);
        }
        List<Map<String, Object>> produtos = new ArrayList<>();
        for (ItemDoPedido i : itens) {
            Map<String, Object> p = new LinkedHashMap<>();
            if (i.produtoId() != null) {
                p.put("productId", i.produtoId());
            }
            p.put("name", i.nome());
            p.put("value", i.valor());
            p.put("quantity", i.quantidade());
            produtos.add(p);
        }
        pedido.put("products", produtos);
        return pedido;
    }

    private static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }

    private static String textoOu(Object valor, String padrao) {
        return valor == null ? padrao : valor.toString();
    }

    private static BigDecimal reais(Object milesimos) {
        if (milesimos == null || milesimos.toString().isBlank()) {
            return null;
        }
        return new BigDecimal(milesimos.toString()).movePointLeft(3).setScale(2, java.math.RoundingMode.HALF_EVEN);
    }

    private static <T> T chamar(Supplier<T> operacao) {
        try {
            return operacao.get();
        } catch (RestClientResponseException e) {
            throw new CanalIndisponivelException("A Z-API recusou a operação (HTTP " + e.getStatusCode().value() + "): "
                    + e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new CanalIndisponivelException("A Z-API não respondeu: " + e.getMessage());
        }
    }
}
