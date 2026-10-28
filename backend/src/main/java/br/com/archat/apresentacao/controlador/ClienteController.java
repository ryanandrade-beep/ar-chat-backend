package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.cliente.AtualizarClienteUseCase;
import br.com.archat.aplicacao.cliente.CadastrarClienteUseCase;
import br.com.archat.aplicacao.cliente.ConsultarClientesUseCase;
import br.com.archat.aplicacao.cliente.ConsultarExtratoUseCase;
import br.com.archat.aplicacao.operador.ConsultarEquipeUseCase;
import br.com.archat.aplicacao.cliente.GerenciarFinanceiroUseCase;
import br.com.archat.apresentacao.dto.ApiEnums;
import br.com.archat.apresentacao.dto.ClienteDtos.BalanceResponse;
import br.com.archat.apresentacao.dto.ClienteDtos.ChangePlanRequest;
import br.com.archat.apresentacao.dto.ClienteDtos.ClientResponse;
import br.com.archat.apresentacao.dto.ClienteDtos.CreateClientRequest;
import br.com.archat.apresentacao.dto.ClienteDtos.CreditRequest;
import br.com.archat.apresentacao.dto.ClienteDtos.LimitRequest;
import br.com.archat.apresentacao.dto.ClienteDtos.TransactionResponse;
import br.com.archat.apresentacao.dto.ClienteDtos.UpdateClientRequest;
import br.com.archat.apresentacao.seguranca.AdministradorAutenticado;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.apresentacao.seguranca.UsuarioAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/clients")
@Tag(name = "Clientes", description = "Cadastro de clientes e movimentação de créditos e limites")
class ClienteController {

    private final CadastrarClienteUseCase cadastrar;
    private final AtualizarClienteUseCase atualizar;
    private final ConsultarClientesUseCase consultar;
    private final GerenciarFinanceiroUseCase financeiro;

    private final ConsultarExtratoUseCase extrato;
    private final ConsultarEquipeUseCase equipe;

    ClienteController(CadastrarClienteUseCase cadastrar, AtualizarClienteUseCase atualizar,
                      ConsultarClientesUseCase consultar, GerenciarFinanceiroUseCase financeiro,
                      ConsultarExtratoUseCase extrato, ConsultarEquipeUseCase equipe) {
        this.cadastrar = cadastrar;
        this.atualizar = atualizar;
        this.consultar = consultar;
        this.financeiro = financeiro;
        this.extrato = extrato;
        this.equipe = equipe;
    }

    @GetMapping
    @Operation(summary = "Lista os clientes (admin)")
    List<ClientResponse> listar(AdministradorAutenticado administrador) {
        return consultar.listar().stream().map(ClientResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um cliente (admin)")
    ClientResponse cadastrar(AdministradorAutenticado administrador, @Valid @RequestBody CreateClientRequest r) {
        return ClientResponse.de(cadastrar.executar(new CadastrarClienteUseCase.Comando(r.name(), r.documentId(),
                ApiEnums.documento(r.documentType()), ApiEnums.plano(r.planType()), r.initialAmount())));
    }

    @GetMapping("/me")
    @Operation(summary = "Dados do cliente autenticado")
    ClientResponse meusDados(ClienteAutenticado cliente) {
        return ClientResponse.de(consultar.buscar(cliente.id()));
    }

    @GetMapping("/me/transactions")
    @Operation(summary = "Extrato do cliente autenticado (últimos 90 dias): créditos, débitos e estornos")
    List<TransactionResponse> meuExtrato(ClienteAutenticado cliente, @RequestParam(defaultValue = "30") int limit) {
        cliente.exigirGestor();
        return extrato.ultimas(cliente.id(), limit).stream().map(TransactionResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhes de um cliente (admin)")
    ClientResponse buscar(AdministradorAutenticado administrador, @PathVariable UUID id) {
        return ClientResponse.de(consultar.buscar(id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera nome e situação de um cliente (admin)")
    ClientResponse atualizar(AdministradorAutenticado administrador, @PathVariable UUID id,
                             @Valid @RequestBody UpdateClientRequest r) {
        return ClientResponse.de(atualizar.executar(id, r.name(), r.active()));
    }

    @GetMapping("/{id}/team")
    @Operation(summary = "Backoffice: responsável (e-mail e telefone) e operadores da workspace (admin)")
    TeamResponse equipe(AdministradorAutenticado administrador, @PathVariable UUID id) {
        consultar.buscar(id);
        var equipe = this.equipe.consultar(id);
        return new TeamResponse(equipe.responsavel().map(c -> new OwnerResponse(c.email(), c.telefone(), c.termosAceitosEm())).orElse(null),
                equipe.operadores().stream().map(o -> new TeamOperator(o.id(), o.nome(), o.email(), o.ativo())).toList());
    }

    record OwnerResponse(String email, String phone, java.time.Instant termsAcceptedAt) {
    }

    record TeamOperator(UUID id, String name, String email, boolean active) {
    }

    record TeamResponse(OwnerResponse owner, List<TeamOperator> operators) {
    }

    @GetMapping("/{id}/balance")
    @Operation(summary = "Saldo ou limite do cliente (admin ou o próprio cliente)")
    BalanceResponse saldo(UsuarioAutenticado usuario, @PathVariable UUID id) {
        usuario.exigirAcessoAoCliente(id);
        return BalanceResponse.de(consultar.buscar(id));
    }

    @PostMapping("/{id}/credits")
    @Operation(summary = "Adiciona créditos a um cliente pré-pago (admin)")
    BalanceResponse adicionarCredito(AdministradorAutenticado administrador, @PathVariable UUID id,
                                     @Valid @RequestBody CreditRequest r) {
        return BalanceResponse.de(financeiro.adicionarCredito(id, r.amount()));
    }

    @PutMapping("/{id}/limit")
    @Operation(summary = "Ajusta o limite mensal de um cliente pós-pago (admin)")
    BalanceResponse ajustarLimite(AdministradorAutenticado administrador, @PathVariable UUID id,
                                  @Valid @RequestBody LimitRequest r) {
        return BalanceResponse.de(financeiro.ajustarLimite(id, r.limit()));
    }

    @PutMapping("/{id}/plan")
    @Operation(summary = "Converte o cliente de plano (admin)")
    BalanceResponse converterPlano(AdministradorAutenticado administrador, @PathVariable UUID id,
                                   @Valid @RequestBody ChangePlanRequest r) {
        return BalanceResponse.de(financeiro.converterPlano(id, ApiEnums.plano(r.planType()), r.limit()));
    }
}
