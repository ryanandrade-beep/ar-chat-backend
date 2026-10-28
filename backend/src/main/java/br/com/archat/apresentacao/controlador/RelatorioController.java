package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.aplicacao.relatorio.PainelDoGestorUseCase;
import br.com.archat.aplicacao.relatorio.Periodo;
import br.com.archat.aplicacao.relatorio.RelatorioAtendimentosUseCase;
import br.com.archat.aplicacao.relatorio.RelatorioCampanhasUseCase;
import br.com.archat.aplicacao.relatorio.RelatorioFaturamentoUseCase;
import br.com.archat.apresentacao.dto.Csv;
import br.com.archat.apresentacao.dto.RelatorioDtos.AttendancesReport;
import br.com.archat.apresentacao.dto.RelatorioDtos.CampaignsReport;
import br.com.archat.apresentacao.dto.RelatorioDtos.RevenueReport;
import br.com.archat.apresentacao.seguranca.AdministradorAutenticado;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.apresentacao.seguranca.UsuarioAutenticado;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Os três relatórios do sistema. O período é de dias inteiros no fuso de Brasília ({@code from} 00:00 até o
 * fim de {@code to}). Com {@code format=csv}, a resposta é um arquivo para abrir no Excel.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "Relatórios", description = "Atendimentos finalizados, faturamento por cliente e desempenho das campanhas")
class RelatorioController {

    private final RelatorioAtendimentosUseCase atendimentos;
    private final RelatorioFaturamentoUseCase faturamento;
    private final RelatorioCampanhasUseCase campanhas;
    private final ZoneId fuso;
    private final PainelDoGestorUseCase painel;
    private final GerenciarOperadoresUseCase operadores;

    RelatorioController(RelatorioAtendimentosUseCase atendimentos, RelatorioFaturamentoUseCase faturamento,
                        RelatorioCampanhasUseCase campanhas, ZoneId fuso, PainelDoGestorUseCase painel,
                        GerenciarOperadoresUseCase operadores) {
        this.painel = painel;
        this.operadores = operadores;
        this.atendimentos = atendimentos;
        this.faturamento = faturamento;
        this.campanhas = campanhas;
        this.fuso = fuso;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "Dashboard do dono: atendimentos do período, abertos, finalizados, 1ª resposta, por dia, "
            + "por operador e os 10 contatos que mais geram atendimento (filtro opcional operatorId)")
    DashboardResponse painel(ClienteAutenticado cliente,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                             @RequestParam(required = false) UUID operatorId) {
        cliente.exigirGestor();
        var p = painel.emitir(cliente.id(), periodo(from, to), operatorId);
        Map<UUID, String> nomes = new HashMap<>();
        operadores.listar(cliente.id()).forEach(o -> nomes.put(o.id(), o.nome()));
        List<OperatorCount> porOperador = p.porOperador().entrySet().stream()
                .map(e -> e.getKey().equals(PainelDoGestorUseCase.SEM_RESPONSAVEL)
                        ? new OperatorCount(null, "Sem responsável", e.getValue())
                        : new OperatorCount(e.getKey(), nomes.getOrDefault(e.getKey(), "?"), e.getValue()))
                .sorted(java.util.Comparator.comparing(OperatorCount::attendances).reversed())
                .toList();
        return new DashboardResponse(from, to, p.atendimentos(), p.abertosAgora(), p.aguardandoAgora(), p.finalizados(),
                p.mediaPrimeiraRespostaSegundos(), p.porDia(), porOperador,
                p.topContatos().stream().map(c -> new TopContact(c.nome(), c.telefone(), c.atendimentos())).toList());
    }

    record OperatorCount(UUID operatorId, String name, long attendances) {
    }

    record TopContact(String name, String phone, long attendances) {
    }

    record DashboardResponse(LocalDate from, LocalDate to, long attendances, long openNow, long waitingNow, long finished,
                             Long averageFirstResponseSeconds, Map<LocalDate, Long> perDay,
                             List<OperatorCount> perOperator, List<TopContact> topContacts) {
    }

    @GetMapping("/attendances")
    @Operation(summary = "Atendimentos finalizados: tempo até a primeira resposta, duração e mensagens por atendimento. "
            + "O cliente vê os seus; o admin pode filtrar por clientId")
    ResponseEntity<?> atendimentos(UsuarioAutenticado usuario, @RequestParam(required = false) UUID clientId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) String format) {
        UUID cliente = usuario.administrador() ? clientId : usuario.sessao().id();
        AttendancesReport r = AttendancesReport.de(atendimentos.emitir(cliente, periodo(from, to)), from, to);
        if (!csv(format)) {
            return ResponseEntity.ok(r);
        }
        return Csv.arquivo("atendimentos-%s-a-%s.csv".formatted(from, to),
                List.of("Destinatário", "Iniciado por", "Aberto em", "Primeira resposta em", "Finalizado em",
                        "Tempo até 1ª resposta (min)", "Duração (min)", "Enviadas", "Recebidas"),
                r.attendances().stream().map(a -> List.<Object>of(a.recipient(),
                        "customer".equals(a.startedBy()) ? "Cliente final" : "Empresa", horario(a.openedAt()),
                        a.firstResponseAt() == null ? "" : horario(a.firstResponseAt()), horario(a.finishedAt()),
                        minutos(a.firstResponseSeconds()), minutos(a.durationSeconds()), a.messagesSent(),
                        a.messagesReceived())).toList());
    }

    @GetMapping("/revenue")
    @Operation(summary = "Faturamento por cliente (admin): ranking por receita líquida, % de urgentes, estornos e ticket médio")
    ResponseEntity<?> faturamento(AdministradorAutenticado administrador,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) String format) {
        RevenueReport r = RevenueReport.de(faturamento.emitir(periodo(from, to)), from, to);
        if (!csv(format)) {
            return ResponseEntity.ok(r);
        }
        return Csv.arquivo("faturamento-%s-a-%s.csv".formatted(from, to),
                List.of("Cliente", "Plano", "Mensagens", "Urgentes", "% urgentes", "Receita bruta (R$)",
                        "Estornos (R$)", "Receita líquida (R$)", "Ticket médio (R$)", "Participação (%)"),
                r.clients().stream().map(c -> List.<Object>of(c.name(), c.planType(), c.messages(), c.urgentMessages(),
                        c.urgentPercentage(), c.grossRevenue(), c.refunds(), c.netRevenue(), c.averageTicket(),
                        c.share())).toList());
    }

    @GetMapping("/campaigns")
    @Operation(summary = "Desempenho das campanhas: enviadas, entregues, lidas, falhas, estorno e custo por entregue. "
            + "O cliente vê as suas; o admin pode filtrar por clientId")
    ResponseEntity<?> campanhas(UsuarioAutenticado usuario, @RequestParam(required = false) UUID clientId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) String format) {
        UUID cliente = usuario.administrador() ? clientId : usuario.sessao().id();
        CampaignsReport r = CampaignsReport.de(campanhas.emitir(cliente, periodo(from, to)), from, to);
        if (!csv(format)) {
            return ResponseEntity.ok(r);
        }
        return Csv.arquivo("campanhas-%s-a-%s.csv".formatted(from, to),
                List.of("Campanha", "Prioridade", "Criada em", "Destinatários", "Enviadas", "Entregues", "Lidas",
                        "Falhas", "Pendentes", "Custo (R$)", "Estornado (R$)", "Custo líquido (R$)",
                        "Taxa de entrega (%)", "Taxa de leitura (%)", "Custo por entregue (R$)"),
                r.campaigns().stream().map(c -> List.<Object>of(c.name(), c.priority(),
                        c.createdAt().atZone(fuso).toLocalDateTime(), c.recipients(), c.sent(), c.delivered(), c.read(),
                        c.failed(), c.pending(), c.totalCost(), c.refunded(), c.netCost(), c.deliveryRate(),
                        c.readRate(), c.costPerDelivered() == null ? "" : c.costPerDelivered())).toList());
    }

    private Periodo periodo(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new RegraDeNegocioException("A data final não pode ser anterior à data inicial");
        }
        return new Periodo(de.atStartOfDay(fuso).toInstant(), ate.plusDays(1).atStartOfDay(fuso).toInstant());
    }

    private String horario(java.time.Instant instante) {
        return instante.atZone(fuso).toLocalDateTime().withNano(0).toString().replace('T', ' ');
    }

    private static Object minutos(Long segundos) {
        return segundos == null ? "" : java.math.BigDecimal.valueOf(segundos).divide(java.math.BigDecimal.valueOf(60), 1,
                java.math.RoundingMode.HALF_EVEN);
    }

    private static boolean csv(String formato) {
        return "csv".equalsIgnoreCase(formato);
    }
}
