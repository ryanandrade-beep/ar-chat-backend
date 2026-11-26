package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.auditoria.ConsultarAuditoriaUseCase;
import br.com.archat.apresentacao.seguranca.AdministradorAutenticado;
import br.com.archat.dominio.auditoria.AcaoDeAuditoria;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Trilha de auditoria para o suporte: quem fez o quê e quando em cada workspace. Só o backoffice consulta. */
@RestController
@Tag(name = "Auditoria", description = "Registro de quem fez cada ação importante nas workspaces (suporte)")
class AuditoriaController {

    private final ConsultarAuditoriaUseCase auditoria;
    private final ZoneId fuso;

    AuditoriaController(ConsultarAuditoriaUseCase auditoria, ZoneId fuso) {
        this.auditoria = auditoria;
        this.fuso = fuso;
    }

    record AuditEntry(UUID id, UUID clientId, String clientName, UUID authorId, String authorName, String action,
                      String actionLabel, String resource, String detail, Instant createdAt) {

        static AuditEntry de(ConsultarAuditoriaUseCase.Entrada e) {
            var r = e.registro();
            String rotulo = AcaoDeAuditoria.porCodigo(r.acao()).map(AcaoDeAuditoria::descricao).orElse(r.acao());
            return new AuditEntry(r.id(), r.clienteId(), e.empresa(), r.autorId(), r.autorNome(), r.acao(), rotulo, r.recurso(),
                    r.detalhe(), r.criadaEm());
        }
    }

    record ActionInfo(String code, String label) {
    }

    @GetMapping("/admin/audit")
    @Operation(summary = "Registros de auditoria de todas as workspaces, do mais recente ao mais antigo (suporte). Filtros: clientId, "
            + "from e to (datas), operatorId, action (código), search (autor, recurso ou detalhe) e limit (padrão 200, máximo 1000)")
    List<AuditEntry> listar(AdministradorAutenticado administrador, @RequestParam(required = false) UUID clientId,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                            @RequestParam(required = false) UUID operatorId, @RequestParam(required = false) String action,
                            @RequestParam(required = false) String search, @RequestParam(required = false) Integer limit) {
        return auditoria.listar(clientId, from == null ? null : from.atStartOfDay(fuso).toInstant(),
                to == null ? null : to.plusDays(1).atStartOfDay(fuso).toInstant(), operatorId, action, search, limit)
                .stream().map(AuditEntry::de).toList();
    }

    @GetMapping("/admin/audit/actions")
    @Operation(summary = "Ações que a auditoria registra, para montar o filtro (suporte)")
    List<ActionInfo> acoes(AdministradorAutenticado administrador) {
        return Arrays.stream(AcaoDeAuditoria.values()).map(a -> new ActionInfo(a.codigo(), a.descricao())).toList();
    }
}
