package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.aplicacao.plano.GerenciarPlanosUseCase;
import br.com.archat.aplicacao.plano.GerenciarPlanosUseCase.DadosPlano;
import br.com.archat.apresentacao.seguranca.AdministradorAutenticado;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.dominio.plano.Assinatura;
import br.com.archat.dominio.plano.Plano;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Planos", description = "Planos do backoffice (mensalidade, operadores e preço por mensagem) e assinaturas")
class PlanoController {

    private final GerenciarPlanosUseCase planos;
    private final GerenciarOperadoresUseCase operadores;

    PlanoController(GerenciarPlanosUseCase planos, GerenciarOperadoresUseCase operadores) {
        this.planos = planos;
        this.operadores = operadores;
    }

    @GetMapping("/plans")
    @Operation(summary = "Planos da plataforma (admin)")
    List<PlanResponse> listar(AdministradorAutenticado administrador) {
        return planos.listar().stream().map(PlanResponse::de).toList();
    }

    @PostMapping("/plans")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um plano (admin)")
    PlanResponse criar(AdministradorAutenticado administrador, @Valid @RequestBody PlanRequest r) {
        return PlanResponse.de(planos.criar(r.dados()));
    }

    @PutMapping("/plans/{id}")
    @Operation(summary = "Altera um plano; vale para as próximas mensagens e mensalidades (admin)")
    PlanResponse alterar(AdministradorAutenticado administrador, @PathVariable UUID id, @Valid @RequestBody PlanRequest r) {
        return PlanResponse.de(planos.alterar(id, r.dados(), r.active() == null || r.active()));
    }

    @PutMapping("/clients/{id}/subscription")
    @Operation(summary = "Vincula a workspace a um plano e cobra a primeira mensalidade (admin)")
    SubscriptionResponse assinar(AdministradorAutenticado administrador, @PathVariable UUID id,
                                 @Valid @RequestBody SubscribeRequest r) {
        planos.assinar(id, r.planId());
        return assinatura(id);
    }

    @GetMapping("/clients/{id}/subscription")
    @Operation(summary = "Plano e situação da assinatura de uma workspace (admin)")
    SubscriptionResponse assinaturaDaWorkspace(AdministradorAutenticado administrador, @PathVariable UUID id) {
        return assinatura(id);
    }

    @GetMapping("/clients/me/subscription")
    @Operation(summary = "Assinatura da própria workspace: plano, preços e operadores em uso (dono)")
    SubscriptionResponse minhaAssinatura(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return assinatura(cliente.id());
    }

    private SubscriptionResponse assinatura(UUID clienteId) {
        long ativos = operadores.listar(clienteId).stream().filter(o -> o.ativo() && !o.dono()).count();
        return planos.assinaturaDe(clienteId)
                .map(a -> SubscriptionResponse.de(a, planos.buscar(a.planoId()), ativos))
                .orElseGet(() -> new SubscriptionResponse(null, null, null, null, ativos));
    }

    record PlanRequest(@NotBlank @Size(max = 60) String name, @NotNull @DecimalMin("0.00") BigDecimal monthlyFee,
                       @NotNull @Min(1) Integer includedOperators, @NotNull @DecimalMin("0.01") BigDecimal normalPrice,
                       @NotNull @DecimalMin("0.01") BigDecimal urgentPrice, Boolean active) {

        DadosPlano dados() {
            return new DadosPlano(name, monthlyFee, includedOperators, normalPrice, urgentPrice);
        }
    }

    record SubscribeRequest(@NotNull UUID planId) {
    }

    record PlanResponse(UUID id, String name, BigDecimal monthlyFee, int includedOperators, BigDecimal normalPrice,
                        BigDecimal urgentPrice, boolean active, Instant createdAt) {

        static PlanResponse de(Plano p) {
            return new PlanResponse(p.id(), p.nome(), p.mensalidade(), p.operadoresIncluidos(), p.precoNormal(),
                    p.precoUrgente(), p.ativo(), p.criadoEm());
        }
    }

    record SubscriptionResponse(PlanResponse plan, String status, Instant since, Instant lastCharge, long activeOperators) {

        static SubscriptionResponse de(Assinatura a, Plano p, long ativos) {
            return new SubscriptionResponse(PlanResponse.de(p), a.situacao() == Assinatura.Situacao.ATIVA ? "active" : "overdue",
                    a.inicio(), a.ultimaCobranca(), ativos);
        }
    }
}
