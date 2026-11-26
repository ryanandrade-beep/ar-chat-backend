package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.departamento.GerenciarDepartamentosUseCase;
import br.com.archat.aplicacao.operador.GerenciarDepartamentosDoOperadorUseCase;
import br.com.archat.apresentacao.seguranca.AuditoriaDaRequisicao;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.dominio.departamento.ConfiguracaoUra;
import br.com.archat.dominio.departamento.Departamento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@RestController
@Tag(name = "Departamentos e URA", description = "Setores da workspace e atendimento automático (menu de departamentos)")
class DepartamentoController {

    private final GerenciarDepartamentosUseCase departamentos;
    private final GerenciarDepartamentosDoOperadorUseCase operadoresDoDepartamento;

    DepartamentoController(GerenciarDepartamentosUseCase departamentos,
                           GerenciarDepartamentosDoOperadorUseCase operadoresDoDepartamento) {
        this.operadoresDoDepartamento = operadoresDoDepartamento;
        this.departamentos = departamentos;
    }

    @GetMapping("/departments")
    @Operation(summary = "Departamentos da workspace (dono e operadores)")
    List<DepartmentResponse> listar(ClienteAutenticado cliente) {
        Map<UUID, List<UUID>> porDepartamento = new HashMap<>();
        operadoresDoDepartamento.doCliente(cliente.id()).forEach((operador, setores) ->
                setores.forEach(setor -> porDepartamento.computeIfAbsent(setor, k -> new ArrayList<>()).add(operador)));
        return departamentos.listar(cliente.id()).stream()
                .map(d -> DepartmentResponse.de(d, porDepartamento.getOrDefault(d.id(), List.of()))).toList();
    }

    @PostMapping("/departments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria um departamento (dono)")
    DepartmentResponse criar(ClienteAutenticado cliente, @Valid @RequestBody DepartmentRequest r) {
        cliente.exigirGestor();
        var criado = departamentos.criar(cliente.id(), r.name());
        AuditoriaDaRequisicao.recurso("Departamento " + criado.nome());
        return DepartmentResponse.de(criado, List.of());
    }

    @PutMapping("/departments/{id}")
    @Operation(summary = "Renomeia, ativa ou desativa um departamento (dono)")
    DepartmentResponse alterar(ClienteAutenticado cliente, @PathVariable UUID id, @Valid @RequestBody DepartmentRequest r) {
        cliente.exigirGestor();
        var alterado = departamentos.alterar(cliente.id(), id, r.name(), r.active() == null || r.active());
        return DepartmentResponse.de(alterado, operadoresDoDepartamento.doCliente(cliente.id()).entrySet().stream()
                .filter(e -> e.getValue().contains(id)).map(Map.Entry::getKey).toList());
    }

    @PutMapping("/departments/{id}/operators")
    @Operation(summary = "Define quais operadores atuam no departamento, inclusive no Geral (dono)")
    DepartmentResponse definirOperadores(ClienteAutenticado cliente, @PathVariable UUID id,
                                         @RequestBody DepartmentOperatorsRequest r) {
        cliente.exigirGestor();
        List<UUID> ids = operadoresDoDepartamento.definirOperadores(cliente.id(), id, r.operatorIds());
        return DepartmentResponse.de(departamentos.buscar(cliente.id(), id), ids);
    }

    @GetMapping("/ivr")
    @Operation(summary = "Configuração da URA (dono)")
    IvrResponse ura(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return IvrResponse.de(departamentos.configuracao(cliente.id()));
    }

    @PutMapping("/ivr")
    @Operation(summary = "Liga, desliga e configura a URA: mensagens ({nome} = nome do contato), tentativas, padrão e grupos (dono)")
    IvrResponse configurar(ClienteAutenticado cliente, @Valid @RequestBody IvrRequest r) {
        cliente.exigirGestor();
        return IvrResponse.de(departamentos.configurar(cliente.id(), r.active(), r.welcomeMessage(), r.questionMessage(),
                r.maxAttempts(), r.defaultDepartmentId(), r.ignoreGroups()));
    }

    record DepartmentRequest(@NotBlank @Size(max = 60) String name, Boolean active) {
    }

    record DepartmentOperatorsRequest(List<UUID> operatorIds) {
    }

    record DepartmentResponse(UUID id, String name, boolean active, boolean general, List<UUID> operatorIds) {

        static DepartmentResponse de(Departamento d, List<UUID> operadores) {
            return new DepartmentResponse(d.id(), d.nome(), d.ativo(), d.geral(), operadores);
        }
    }

    record IvrRequest(boolean active, @NotBlank @Size(max = 1000) String welcomeMessage,
                      @NotBlank @Size(max = 1000) String questionMessage, @NotNull @Min(1) @Max(10) Integer maxAttempts,
                      UUID defaultDepartmentId, boolean ignoreGroups) {
    }

    record IvrResponse(boolean active, String welcomeMessage, String questionMessage, int maxAttempts,
                       UUID defaultDepartmentId, boolean ignoreGroups) {

        static IvrResponse de(ConfiguracaoUra c) {
            return new IvrResponse(c.ativa(), c.mensagemBoasVindas(), c.mensagemPergunta(), c.maximoTentativas(),
                    c.departamentoPadraoId(), c.ignorarGrupos());
        }
    }
}
