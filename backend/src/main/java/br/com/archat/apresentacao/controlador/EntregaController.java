package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.entrega.GerenciarEntregasUseCase;
import br.com.archat.aplicacao.entrega.GerenciarEntregasUseCase.Resultado;
import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.aplicacao.porta.CatalogoWhatsApp;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.dominio.entrega.ConfiguracaoDeEntrega;
import br.com.archat.dominio.entrega.Entrega;
import br.com.archat.dominio.entrega.StatusEntrega;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Entregas pelo WhatsApp e catálogo. O operador registra a entrega ao encerrar o atendimento; o responsável da conta
 * acompanha, confirma que saiu (o cliente é avisado), que foi entregue, cancela e configura os avisos e o Pix.
 */
@RestController
@Tag(name = "Entregas", description = "Entregas de pedidos, avisos ao cliente pelo WhatsApp e catálogo de produtos")
class EntregaController {

    private final GerenciarEntregasUseCase entregas;
    private final GerenciarOperadoresUseCase operadores;
    private final CatalogoWhatsApp catalogo;
    private final ZoneId fuso;

    EntregaController(GerenciarEntregasUseCase entregas, GerenciarOperadoresUseCase operadores, CatalogoWhatsApp catalogo,
                      ZoneId fuso) {
        this.entregas = entregas;
        this.operadores = operadores;
        this.catalogo = catalogo;
        this.fuso = fuso;
    }

    record DeliveryItem(String productId, String name, Integer quantity, BigDecimal unitPrice) {
    }

    record DeliveryResponse(UUID id, UUID conversationId, String customerName, String phone, String address, String order,
                            List<DeliveryItem> items, BigDecimal value, String paymentMethod, String note, String status,
                            String origin, Instant createdAt, Instant dispatchedAt, Instant deliveredAt, Instant canceledAt,
                            String cancelReason, String operatorName, Boolean departureNoticeSent,
                            Boolean deliveryNoticeSent, boolean nativeOrderLinked) {
    }

    record ActionResponse(DeliveryResponse delivery, Boolean noticeSent, boolean nativeStatusUpdated) {
    }

    record CancelRequest(String reason) {
    }

    record UpdateRequest(String address, String note) {
    }

    record ChargeRequest(BigDecimal shipping) {
    }

    record SettingsRequest(boolean notifyDeparture, String departureMessage, boolean notifyDelivered, String deliveredMessage,
                           String pixKey, String pixKeyType, String pixName, boolean acceptCard) {
    }

    record SettingsResponse(boolean notifyDeparture, String departureMessage, boolean notifyDelivered, String deliveredMessage,
                            String pixKey, String pixKeyType, String pixName, boolean acceptCard) {

        static SettingsResponse de(ConfiguracaoDeEntrega c) {
            return new SettingsResponse(c.avisarSaida(), c.mensagemSaida(), c.avisarEntregue(), c.mensagemEntregue(),
                    c.pixChave(), c.pixTipo(), c.pixNome(), c.aceitaCartao());
        }
    }

    record SummaryResponse(Map<String, Long> byStatus, Long averageMinutesToDeliver, Long longestOnTheRoadMinutes) {
    }

    record ProductResponse(String id, String name, String description, BigDecimal price, BigDecimal salePrice, String image,
                           String availability, String code) {
    }

    record CatalogResponse(boolean cartEnabled, List<ProductResponse> products, String nextCursor) {
    }

    @GetMapping("/deliveries")
    @Operation(summary = "Entregas da workspace (responsável): filtros status (pending, on_the_way, delivered, canceled), "
            + "from, to (data de registro) e search (cliente, telefone, endereço ou pedido)")
    List<DeliveryResponse> listar(ClienteAutenticado cliente, @RequestParam(required = false) String status,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) String search) {
        cliente.exigirGestor();
        Map<UUID, String> nomes = nomesDosOperadores(cliente.id());
        return entregas.listar(cliente.id(), status(status), from == null ? null : from.atStartOfDay(fuso).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(fuso).toInstant(), search).stream()
                .map(e -> resposta(e, nomes)).toList();
    }

    @GetMapping("/deliveries/summary")
    @Operation(summary = "Contadores por status, tempo médio na rua e a entrega que está há mais tempo a caminho (responsável)")
    SummaryResponse resumo(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        var r = entregas.resumo(cliente.id());
        Map<String, Long> porStatus = new LinkedHashMap<>();
        r.porStatus().forEach((s, n) -> porStatus.put(nomeDoStatus(s), n));
        return new SummaryResponse(porStatus, r.minutosMedioAteEntregar(), r.minutosMaisLongaNaRua());
    }

    @PostMapping("/deliveries/{id}/dispatch")
    @Operation(summary = "Confirma que a entrega saiu: o cliente recebe o aviso \"a caminho\" no WhatsApp (responsável)")
    ActionResponse saiu(ClienteAutenticado cliente, @PathVariable UUID id) {
        cliente.exigirGestor();
        return acao(cliente, entregas.saiu(cliente.id(), id));
    }

    @PostMapping("/deliveries/{id}/deliver")
    @Operation(summary = "Confirma que a entrega foi feita; o cliente recebe o aviso de entregue (responsável)")
    ActionResponse entregue(ClienteAutenticado cliente, @PathVariable UUID id) {
        cliente.exigirGestor();
        return acao(cliente, entregas.entregue(cliente.id(), id));
    }

    @PostMapping("/deliveries/{id}/cancel")
    @Operation(summary = "Cancela a entrega (responsável)")
    DeliveryResponse cancelar(ClienteAutenticado cliente, @PathVariable UUID id, @RequestBody(required = false) CancelRequest r) {
        cliente.exigirGestor();
        return resposta(entregas.cancelar(cliente.id(), id, r == null ? null : r.reason()), nomesDosOperadores(cliente.id()));
    }

    @PutMapping("/deliveries/{id}")
    @Operation(summary = "Corrige o endereço e a observação de uma entrega em aberto (responsável)")
    DeliveryResponse corrigir(ClienteAutenticado cliente, @PathVariable UUID id, @RequestBody UpdateRequest r) {
        cliente.exigirGestor();
        return resposta(entregas.corrigir(cliente.id(), id, r.address(), r.note()), nomesDosOperadores(cliente.id()));
    }

    @PostMapping("/deliveries/{id}/charge")
    @Operation(summary = "Envia ao cliente o cartão do pedido com Pix e cartão (WhatsApp Business) (responsável)")
    void cobrar(ClienteAutenticado cliente, @PathVariable UUID id, @RequestBody(required = false) ChargeRequest r) {
        cliente.exigirGestor();
        entregas.enviarCobranca(cliente.id(), id, r == null ? null : r.shipping());
    }

    @GetMapping("/deliveries/settings")
    @Operation(summary = "Avisos automáticos e chave Pix das entregas (responsável)")
    SettingsResponse configuracao(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return SettingsResponse.de(entregas.configuracao(cliente.id()));
    }

    @PutMapping("/deliveries/settings")
    @Operation(summary = "Define os avisos automáticos ({nome} vira o nome do cliente) e a chave Pix (responsável)")
    SettingsResponse configurar(ClienteAutenticado cliente, @RequestBody SettingsRequest r) {
        cliente.exigirGestor();
        return SettingsResponse.de(entregas.configurar(new ConfiguracaoDeEntrega(cliente.id(), r.notifyDeparture(),
                r.departureMessage(), r.notifyDelivered(), r.deliveredMessage(), r.pixKey(), r.pixKeyType(), r.pixName(),
                r.acceptCard())));
    }

    @GetMapping("/catalog/products")
    @Operation(summary = "Produtos do catálogo do WhatsApp Business, 10 por página: passe cursor para a próxima")
    CatalogResponse produtos(ClienteAutenticado cliente, @RequestParam(required = false) String cursor) {
        var pagina = catalogo.listar(cliente.id(), cursor);
        return new CatalogResponse(pagina.carrinhoAtivo(), pagina.produtos().stream()
                .filter(p -> !p.oculto())
                .map(p -> new ProductResponse(p.id(), p.nome(), p.descricao(), p.preco(), p.precoPromocional(), p.imagem(),
                        p.disponibilidade(), p.codigoInterno())).toList(), pagina.proximoCursor());
    }

    private ActionResponse acao(ClienteAutenticado cliente, Resultado r) {
        return new ActionResponse(resposta(r.entrega(), nomesDosOperadores(cliente.id())), r.avisoEnviado(), r.pelaPedidoNativo());
    }

    private DeliveryResponse resposta(Entrega e, Map<UUID, String> nomes) {
        return new DeliveryResponse(e.id(), e.conversaId(), e.nomeCliente(), e.telefone(), e.endereco(), e.pedido(),
                e.itens().stream().map(i -> new DeliveryItem(i.produtoId(), i.nome(), i.quantidade(), i.valorUnitario())).toList(),
                e.valor(), e.pagamento(), e.observacao(), nomeDoStatus(e.status()), e.origem().name().toLowerCase(Locale.ROOT),
                e.criadaEm(), e.saiuEm(), e.entregueEm(), e.canceladaEm(), e.motivoCancelamento(),
                e.operadorId() == null ? null : nomes.get(e.operadorId()), e.avisoSaidaEnviado(), e.avisoEntregaEnviado(),
                e.referenciaPedido() != null && e.requisicaoPedido() != null);
    }

    private Map<UUID, String> nomesDosOperadores(UUID clienteId) {
        Map<UUID, String> nomes = new HashMap<>();
        for (Operador o : operadores.listar(clienteId)) {
            nomes.put(o.id(), o.nome());
        }
        return nomes;
    }

    private static StatusEntrega status(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return switch (valor) {
            case "pending" -> StatusEntrega.AGUARDANDO;
            case "on_the_way" -> StatusEntrega.A_CAMINHO;
            case "delivered" -> StatusEntrega.ENTREGUE;
            case "canceled" -> StatusEntrega.CANCELADA;
            default -> throw new RegraDeNegocioException("status deve ser pending, on_the_way, delivered ou canceled");
        };
    }

    static String nomeDoStatus(StatusEntrega s) {
        return switch (s) {
            case AGUARDANDO -> "pending";
            case A_CAMINHO -> "on_the_way";
            case ENTREGUE -> "delivered";
            case CANCELADA -> "canceled";
        };
    }
}
