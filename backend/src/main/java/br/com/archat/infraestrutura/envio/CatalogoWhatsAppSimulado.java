package br.com.archat.infraestrutura.envio;

import br.com.archat.aplicacao.porta.CatalogoWhatsApp;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Catálogo de demonstração para o modo simulado (sem Z-API): não envia nada, só registra no log. */
@Component
@ConditionalOnProperty(name = "archat.envio.provedor", havingValue = "simulado", matchIfMissing = true)
class CatalogoWhatsAppSimulado implements CatalogoWhatsApp {

    @Override
    public PaginaDoCatalogo listar(UUID clienteId, String cursor) {
        return new PaginaDoCatalogo(true, List.of(
                new Produto("1001", "Picanha Bovina (kg)", "Picanha fresca", new BigDecimal("69.90"), null, "BRL", null,
                        "in stock", false, "AR-PICANHA"),
                new Produto("1002", "Pão Francês (kg)", "Pão quentinho", new BigDecimal("18.90"), null, "BRL", null,
                        "in stock", false, "AR-PAO")), null);
    }

    @Override
    public void enviarPedido(UUID clienteId, String telefone, PedidoParaEnvio pedido) {
        LoggerFactory.getLogger(CatalogoWhatsAppSimulado.class).info("Pedido simulado para {}: {} item(ns)", telefone,
                pedido.itens().size());
    }

    @Override
    public void atualizarStatusDoPedido(UUID clienteId, String telefone, AtualizacaoDePedido atualizacao) {
        LoggerFactory.getLogger(CatalogoWhatsAppSimulado.class).info("Status de pedido simulado para {}: {}", telefone,
                atualizacao.statusDoPedido());
    }
}
