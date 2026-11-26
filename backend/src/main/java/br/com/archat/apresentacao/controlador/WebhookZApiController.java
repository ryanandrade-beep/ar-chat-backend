package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.atendimento.ReceberMensagemUseCase;
import br.com.archat.aplicacao.entrega.GerenciarEntregasUseCase;
import br.com.archat.aplicacao.excecao.NaoAutenticadoException;
import br.com.archat.dominio.entrega.ItemDeEntrega;
import br.com.archat.aplicacao.mensagem.AtualizarStatusPeloCanalUseCase;
import br.com.archat.aplicacao.mensagem.AtualizarStatusPeloCanalUseCase.AvisoDoCanal;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.ContatoCompartilhado;
import br.com.archat.dominio.mensagem.Midia;
import br.com.archat.dominio.mensagem.TipoMidia;
import br.com.archat.aplicacao.porta.InstanciaWhatsAppRepositorio;
import br.com.archat.infraestrutura.config.ArChatPropriedades;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Webhooks da Z-API (https://developer.z-api.io/webhooks/introduction), no formato da documentação:
 * "Ao receber" (ReceivedCallback) e "Status da mensagem" (MessageStatusCallback). A Z-API não assina as
 * chamadas, então a URL carrega um segredo; sem ele, a chamada é recusada. Configure na instância:
 * https://SEU-ENDERECO/webhooks/zapi/{segredo}/received e .../status (a Z-API exige HTTPS).
 */
@RestController
@RequestMapping("/webhooks/zapi/{segredo}")
@Tag(name = "Webhooks Z-API", description = "Chamados pela Z-API; não usam token, e sim o segredo na URL")
class WebhookZApiController {
    private static final Logger log = LoggerFactory.getLogger(WebhookZApiController.class);

    private final ReceberMensagemUseCase receber;
    private final AtualizarStatusPeloCanalUseCase atualizarStatus;
    private final byte[] segredo;

    private final InstanciaWhatsAppRepositorio instancias;
    private final GerenciarEntregasUseCase entregas;

    WebhookZApiController(ReceberMensagemUseCase receber, AtualizarStatusPeloCanalUseCase atualizarStatus,
                          ArChatPropriedades propriedades,
                          InstanciaWhatsAppRepositorio instancias, GerenciarEntregasUseCase entregas) {
        this.entregas = entregas;
        this.instancias = instancias;
        this.receber = receber;
        this.atualizarStatus = atualizarStatus;
        this.segredo = propriedades.webhook().segredo().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/received")
    @Operation(summary = "Ao receber: mensagem enviada por um contato ao número conectado")
    Map<String, Object> recebida(@PathVariable String segredo, @RequestBody RecebidaZApi corpo) {
        validar(segredo);
        if (Boolean.TRUE.equals(corpo.fromMe()) || !"ReceivedCallback".equals(corpo.type()) || corpo.reaction() != null) {
            return Map.of("ignored", true);
        }
        Instant quando = corpo.momment() == null ? null : Instant.ofEpochMilli(corpo.momment());
        UUID empresa = instancias.buscarPorInstancia(corpo.instanceId()).map(i -> i.clienteId()).orElse(null);
        var pedido = corpo.pedido();
        if (pedido != null && empresa != null) {
            return pedidoDoCatalogo(empresa, corpo, pedido, quando);
        }
        var revisao = corpo.revisaoDoPedido();
        if (revisao != null && empresa != null) {
            boolean vinculado = entregas.vincularPedidoNativo(empresa, corpo.phone(), revisao.referenceId(),
                    revisao.orderRequestId());
            return Map.of("orderLinked", vinculado);
        }
        boolean registrada = (corpo.contato() != null
                ? receber.contatoDoCanal(empresa, corpo.phone(), corpo.nome(), corpo.contato(), corpo.messageId(), quando)
                : receber.doCanal(empresa, corpo.phone(), corpo.nome(), corpo.textoOuLegenda(), corpo.midia(), corpo.messageId(), quando))
                .isPresent();
        if (!registrada) {
            log.info("Webhook Z-API: mensagem {} de {} ignorada ({})", corpo.messageId(), corpo.phone(),
                    empresa == null ? "instância " + corpo.instanceId() + " não vinculada a nenhuma empresa; vincule no backoffice"
                            : "repetida ou sem conteúdo");
        }
        return Map.of("registered", registrada);
    }

    private Map<String, Object> pedidoDoCatalogo(UUID empresa, RecebidaZApi corpo, RecebidaZApi.Pedido pedido, Instant quando) {
        List<ItemDeEntrega> itens = pedido.itens();
        BigDecimal total = itens.stream().map(ItemDeEntrega::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        String resumo = "Pedido pelo catálogo: " + itens.stream().map(i -> i.quantidade() + "x " + i.nome())
                .collect(java.util.stream.Collectors.joining(", "))
                + (total.signum() > 0 ? " · Total R$ " + total.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString().replace('.', ',') : "");
        var fotos = entregas.imagensDosProdutos(empresa, itens.stream().map(ItemDeEntrega::produtoId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet()));
        itens = itens.stream().map(it -> new ItemDeEntrega(it.produtoId(), it.nome(), it.quantidade(), it.valorUnitario(),
                fotos.get(it.produtoId()))).toList();
        var mensagem = receber.doCanal(empresa, corpo.phone(), corpo.nome(), resumo, null, corpo.messageId(), quando);
        if (mensagem.isEmpty() || itens.isEmpty()) {
            return Map.of("registered", mensagem.isPresent());
        }
        entregas.registrarPedidoDoCatalogo(empresa, mensagem.get().conversaId(), itens, resumo, mensagem.get().id());
        return Map.of("registered", true, "delivery", true);
    }

    @PostMapping("/status")
    @Operation(summary = "Status da mensagem: RECEIVED (entregue) e READ/PLAYED (lida)")
    Map<String, Object> status(@PathVariable String segredo, @RequestBody StatusZApi corpo) {
        validar(segredo);
        AvisoDoCanal aviso = switch (corpo.status() == null ? "" : corpo.status()) {
            case "RECEIVED" -> AvisoDoCanal.ENTREGUE;
            case "READ", "PLAYED" -> AvisoDoCanal.LIDA;
            default -> null;
        };
        if (aviso == null || corpo.ids() == null) {
            return Map.of("updated", 0);
        }
        return Map.of("updated", atualizarStatus.aplicar(corpo.ids(), aviso));
    }

    private void validar(String informado) {
        if (!MessageDigest.isEqual(segredo, informado.getBytes(StandardCharsets.UTF_8))) {
            throw new NaoAutenticadoException("Webhook com segredo inválido");
        }
    }

    record RecebidaZApi(String instanceId, String type, String messageId, String phone, Boolean fromMe, Boolean isGroup,
                        Long momment, String senderName, String chatName, Object reaction, Texto text, Imagem image, Audio audio, Video video,
                        Documento document, Contato contact, PedidoZApi order, RevisaoPedido reviewOrder,
                        RevisaoPedido reviewAndPay) {
        Pedido pedido() {
            if (order == null || order.products() == null) return null;
            try {
                return new Pedido(order.products().stream().filter(p -> p.name() != null && p.quantity() != null && p.quantity() > 0)
                        .map(p -> new ItemDeEntrega(p.productId(), p.name(), p.quantity(), p.valor())).toList());
            } catch (RegraDeNegocioException e) {
                return null;
            }
        }

        RevisaoPedido revisaoDoPedido() {
            RevisaoPedido r = reviewOrder != null ? reviewOrder : reviewAndPay;
            return r == null || r.referenceId() == null || r.orderRequestId() == null ? null : r;
        }

        record Pedido(List<ItemDeEntrega> itens) {
        }

        record PedidoZApi(String orderId, List<ProdutoDoPedido> products) {
        }

        /** O pedido chega com o preço em reais (confirmado com um pedido real: R$ 10 veio como 10); sem preço, valor zero. */
        record ProdutoDoPedido(String productId, String name, Integer quantity, BigDecimal price, BigDecimal unitPrice) {
            BigDecimal valor() {
                BigDecimal bruto = unitPrice != null ? unitPrice : price;
                return bruto == null ? BigDecimal.ZERO : bruto;
            }
        }

        record RevisaoPedido(String referenceId, String orderRequestId) {
        }

        String nome() {
            return Boolean.TRUE.equals(isGroup) && chatName != null && !chatName.isBlank() ? chatName : senderName;
        }

        ContatoCompartilhado contato() {
            if (contact == null || contact.phones() == null || contact.phones().isEmpty()) return null;
            try {
                return new ContatoCompartilhado(contact.displayName(), contact.phones().get(0));
            } catch (RegraDeNegocioException e) {
                return null;
            }
        }

        record Contato(String displayName, String vCard, List<String> phones) {
        }

        String textoOuLegenda() {
            if (text != null) return text.message();
            if (image != null) return image.caption();
            if (video != null) return video.caption();
            if (document != null) return document.caption();
            return null;
        }

        Midia midia() {
            try {
                if (image != null && image.imageUrl() != null) return new Midia(TipoMidia.IMAGEM, image.imageUrl(), null);
                if (audio != null && audio.audioUrl() != null) return new Midia(TipoMidia.AUDIO, audio.audioUrl(), null);
                if (video != null && video.videoUrl() != null) return new Midia(TipoMidia.VIDEO, video.videoUrl(), null);
                if (document != null && document.documentUrl() != null) {
                    return new Midia(TipoMidia.DOCUMENTO, document.documentUrl(), document.fileName());
                }
            } catch (RegraDeNegocioException e) {
                return null;
            }
            return null;
        }

        record Texto(String message) {
        }

        record Imagem(String imageUrl, String caption, String mimeType) {
        }

        record Audio(String audioUrl, String mimeType) {
        }

        record Video(String videoUrl, String caption, String mimeType) {
        }

        record Documento(String documentUrl, String fileName, String title, String caption, String mimeType) {
        }
    }

    record StatusZApi(String type, String status, List<String> ids, String phone, Boolean isGroup) {
    }
}
