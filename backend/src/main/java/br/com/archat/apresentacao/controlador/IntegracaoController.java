package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.integracao.GerenciarIntegracaoUseCase;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.integracao.ConfiguracaoDeIntegracao;
import br.com.archat.dominio.integracao.EnvioDeIntegracao;
import br.com.archat.dominio.integracao.EventoDeIntegracao;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Webhooks de saída: o responsável cadastra a URL do CRM e escolhe os eventos que quer receber. */
@RestController
@Tag(name = "Integrações", description = "Avisos (webhooks) enviados ao sistema da empresa: CRM, ERP, planilhas")
class IntegracaoController {

    private final GerenciarIntegracaoUseCase integracao;

    IntegracaoController(GerenciarIntegracaoUseCase integracao) {
        this.integracao = integracao;
    }

    record EventInfo(String code, String description) {
    }

    record WebhookRequest(String url, List<String> events, Boolean active) {
    }

    record WebhookResponse(boolean configured, String url, String secret, List<String> events, boolean active,
                           List<EventInfo> availableEvents) {
    }

    record DeliveryResponse(UUID id, String event, String status, int attempts, Integer httpStatus, String response,
                            Instant createdAt, Instant sentAt, Instant nextAttemptAt, String payload) {

        static DeliveryResponse de(EnvioDeIntegracao e) {
            return new DeliveryResponse(e.id(), e.evento(), e.status().name().toLowerCase(), e.tentativas(), e.httpStatus(),
                    e.resposta(), e.criadoEm(), e.enviadoEm(), e.proximaTentativa(), e.payload());
        }
    }

    @GetMapping("/integrations/webhook")
    @Operation(summary = "Configuração do webhook de saída da empresa e o catálogo de eventos (responsável)")
    WebhookResponse obter(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return resposta(integracao.obter(cliente.id()).orElse(null));
    }

    @PutMapping("/integrations/webhook")
    @Operation(summary = "Define a URL (https) e os eventos; na primeira vez gera o segredo de assinatura (responsável)")
    WebhookResponse salvar(ClienteAutenticado cliente, @RequestBody WebhookRequest r) {
        cliente.exigirGestor();
        Set<EventoDeIntegracao> eventos = EnumSet.noneOf(EventoDeIntegracao.class);
        for (String codigo : r.events() == null ? List.<String>of() : r.events()) {
            eventos.add(EventoDeIntegracao.porCodigo(codigo).filter(EventoDeIntegracao::assinavel)
                    .orElseThrow(() -> new RegraDeNegocioException("Evento desconhecido: " + codigo)));
        }
        return resposta(integracao.salvar(cliente.id(), r.url(), eventos, r.active() == null || r.active()));
    }

    @PostMapping("/integrations/webhook/secret")
    @Operation(summary = "Gera um novo segredo de assinatura; o anterior deixa de valer (responsável)")
    WebhookResponse novoSegredo(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return resposta(integracao.regenerarSegredo(cliente.id()));
    }

    @PostMapping("/integrations/webhook/test")
    @Operation(summary = "Envia agora um evento de teste (test.ping) e devolve o resultado (responsável)")
    DeliveryResponse testar(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return DeliveryResponse.de(integracao.testar(cliente.id()));
    }

    @GetMapping("/integrations/webhook/deliveries")
    @Operation(summary = "Últimos 50 envios, com status, tentativas e a resposta do sistema da empresa (responsável)")
    List<DeliveryResponse> envios(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return integracao.ultimos(cliente.id()).stream().map(DeliveryResponse::de).toList();
    }

    @PostMapping("/integrations/webhook/deliveries/{id}/retry")
    @Operation(summary = "Coloca um envio de volta na fila para nova tentativa (responsável)")
    DeliveryResponse reenviar(ClienteAutenticado cliente, @PathVariable UUID id) {
        cliente.exigirGestor();
        return DeliveryResponse.de(integracao.reenviar(cliente.id(), id));
    }

    private static WebhookResponse resposta(ConfiguracaoDeIntegracao c) {
        List<EventInfo> catalogo = Arrays.stream(EventoDeIntegracao.values()).filter(EventoDeIntegracao::assinavel)
                .map(e -> new EventInfo(e.codigo(), e.descricao())).toList();
        if (c == null) {
            return new WebhookResponse(false, null, null, List.of(), false, catalogo);
        }
        return new WebhookResponse(true, c.url(), c.segredo(),
                c.eventos().stream().map(EventoDeIntegracao::codigo).sorted().toList(), c.ativa(), catalogo);
    }
}
