package br.com.archat.aplicacao.integracao;

import br.com.archat.dominio.entrega.Entrega;
import br.com.archat.dominio.mensagem.Mensagem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Monta os blocos de dados dos eventos, sempre no formato do A&R Chat (nada do provedor de WhatsApp). */
public final class DadosDosEventos {

    private DadosDosEventos() {
    }

    public static Map<String, Object> contato(String nome, String telefone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", nome);
        m.put("phone", telefone);
        return m;
    }

    public static Map<String, Object> mensagem(Mensagem mensagem) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", mensagem.id().toString());
        if (mensagem.contato() != null) {
            m.put("type", "contact");
            m.put("contact", contato(mensagem.contato().nome(), mensagem.contato().telefone()));
        } else if (mensagem.midia() != null) {
            m.put("type", switch (mensagem.midia().tipo()) {
                case IMAGEM -> "image";
                case AUDIO -> "audio";
                case VIDEO -> "video";
                case DOCUMENTO -> "document";
            });
            m.put("mediaUrl", mensagem.midia().url());
            m.put("fileName", mensagem.midia().nomeArquivo());
            m.put("text", mensagem.conteudo() == null || mensagem.conteudo().isBlank() ? null : mensagem.conteudo());
        } else {
            m.put("type", "text");
            m.put("text", mensagem.conteudo());
        }
        return m;
    }

    public static Map<String, Object> entrega(Entrega e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("deliveryId", e.id().toString());
        m.put("conversationId", e.conversaId().toString());
        m.put("contact", contato(e.nomeCliente(), e.telefone()));
        m.put("address", e.endereco());
        m.put("order", e.pedido());
        List<Map<String, Object>> itens = new ArrayList<>();
        e.itens().forEach(i -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("productId", i.produtoId());
            item.put("name", i.nome());
            item.put("quantity", i.quantidade());
            item.put("unitPrice", i.valorUnitario());
            itens.add(item);
        });
        m.put("items", itens);
        m.put("value", e.valor());
        m.put("paymentMethod", e.pagamento());
        m.put("status", switch (e.status()) {
            case AGUARDANDO -> "pending";
            case A_CAMINHO -> "on_the_way";
            case ENTREGUE -> "delivered";
            case CANCELADA -> "canceled";
        });
        m.put("origin", e.origem().name().toLowerCase(Locale.ROOT));
        m.put("dispatchedAt", e.saiuEm() == null ? null : e.saiuEm().toString());
        m.put("deliveredAt", e.entregueEm() == null ? null : e.entregueEm().toString());
        return m;
    }
}
