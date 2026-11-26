package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.operador.GerenciarDepartamentosDoOperadorUseCase;
import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.apresentacao.seguranca.AuditoriaDaRequisicao;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.dominio.cliente.Operador;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/operators")
@Tag(name = "Operadores", description = "Atendentes da workspace, cada um com login próprio (gerenciados pelo dono)")
class OperadorController {

    private final GerenciarOperadoresUseCase operadores;
    private final GerenciarDepartamentosDoOperadorUseCase departamentos;

    OperadorController(GerenciarOperadoresUseCase operadores, GerenciarDepartamentosDoOperadorUseCase departamentos) {
        this.departamentos = departamentos;
        this.operadores = operadores;
    }

    @GetMapping
    @Operation(summary = "Operadores da workspace (o dono e os operadores podem listar, para transferir conversas)")
    List<OperatorResponse> listar(ClienteAutenticado cliente) {
        Map<UUID, List<UUID>> vinculos = departamentos.doCliente(cliente.id());
        return operadores.listar(cliente.id()).stream()
                .map(o -> OperatorResponse.de(o, vinculos.getOrDefault(o.id(), List.of()))).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um operador com login (e-mail) e senha próprios (somente o dono)")
    OperatorResponse cadastrar(ClienteAutenticado cliente, @Valid @RequestBody CreateOperatorRequest r) {
        cliente.exigirGestor();
        var criado = operadores.cadastrar(cliente.id(), r.name(), r.email(), r.password());
        AuditoriaDaRequisicao.recurso("Operador " + criado.nome());
        return OperatorResponse.de(criado, r.departmentIds() == null ? List.of()
                : departamentos.definir(cliente.id(), criado.id(), r.departmentIds()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera o nome e o e-mail de login e ativa ou desativa o operador (somente o dono)")
    OperatorResponse alterar(ClienteAutenticado cliente, @PathVariable UUID id, @Valid @RequestBody UpdateOperatorRequest r) {
        cliente.exigirGestor();
        if (operadores.buscar(cliente.id(), id).dono()) {
            throw new br.com.archat.dominio.excecao.RegraDeNegocioException("O perfil do responsável não pode ser alterado aqui");
        }
        var alterado = operadores.alterar(cliente.id(), id, r.name(), r.email(), r.active());
        List<UUID> vinculados = r.departmentIds() == null
                ? departamentos.doCliente(cliente.id()).getOrDefault(id, List.of())
                : departamentos.definir(cliente.id(), id, r.departmentIds());
        return OperatorResponse.de(alterado, vinculados);
    }

    @PutMapping("/{id}/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Define uma nova senha para o operador (somente o dono)")
    void redefinirSenha(ClienteAutenticado cliente, @PathVariable UUID id, @Valid @RequestBody PasswordRequest r) {
        cliente.exigirGestor();
        if (operadores.buscar(cliente.id(), id).dono()) {
            throw new br.com.archat.dominio.excecao.RegraDeNegocioException("O responsável troca a senha pela própria conta");
        }
        operadores.redefinirSenha(cliente.id(), id, r.password());
    }

    record CreateOperatorRequest(@NotBlank @Size(max = 120) String name, @NotBlank String email, @NotBlank String password,
                                 List<UUID> departmentIds) {
    }

    record UpdateOperatorRequest(@NotBlank @Size(max = 120) String name, String email, @NotNull Boolean active,
                                 List<UUID> departmentIds) {
    }

    record PasswordRequest(@NotBlank String password) {
    }

    record OperatorResponse(UUID id, String name, String email, boolean active, Instant createdAt, boolean owner,
                            List<UUID> departmentIds) {

        static OperatorResponse de(Operador o, List<UUID> departamentos) {
            return new OperatorResponse(o.id(), o.nome(), o.dono() ? null : o.email(), o.ativo(), o.criadoEm(), o.dono(),
                    departamentos);
        }
    }
}
