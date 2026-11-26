package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.departamento.GerenciarDepartamentosUseCase;
import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.aplicacao.relatorio.PainelDoGestorUseCase;
import br.com.archat.aplicacao.relatorio.Periodo;
import br.com.archat.aplicacao.relatorio.RelatorioAtendimentosUseCase;
import br.com.archat.aplicacao.relatorio.RelatorioCampanhasUseCase;
import br.com.archat.aplicacao.relatorio.RelatorioFaturamentoUseCase;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.apresentacao.dto.ApiEnums;
import br.com.archat.apresentacao.dto.Csv;
import br.com.archat.apresentacao.dto.Pdf;
import br.com.archat.apresentacao.dto.RelatorioDtos.AttendancesReport;
import br.com.archat.apresentacao.dto.RelatorioDtos.CampaignsReport;
import br.com.archat.apresentacao.dto.RelatorioDtos.RevenueReport;
import br.com.archat.apresentacao.seguranca.AdministradorAutenticado;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.apresentacao.seguranca.UsuarioAutenticado;
import br.com.archat.dominio.atendimento.OrigemAtendimento;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.TipoPlano;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Prioridade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Os três relatórios do sistema. O período é de dias inteiros no fuso de Brasília ({@code from} 00:00 até o
 * fim de {@code to}). Com {@code format=csv}, a resposta é um arquivo para abrir no Excel; com {@code format=pdf},
 * o relatório pronto para imprimir, com o logo, a empresa, o período e os filtros aplicados.
 */
@RestController
@RequestMapping("/reports")
@Tag(name = "Relatórios", description = "Atendimentos finalizados, faturamento por cliente e desempenho das campanhas")
class RelatorioController {

    private final RelatorioAtendimentosUseCase atendimentos;
    private final RelatorioFaturamentoUseCase faturamento;
    private final RelatorioCampanhasUseCase campanhas;
    private static final java.util.Locale BRASIL = java.util.Locale.forLanguageTag("pt-BR");

    private final ZoneId fuso;
    private final PainelDoGestorUseCase painel;
    private final GerenciarOperadoresUseCase operadores;
    private final ClienteRepositorio clientes;
    private final GerenciarDepartamentosUseCase departamentos;

    RelatorioController(RelatorioAtendimentosUseCase atendimentos, RelatorioFaturamentoUseCase faturamento,
                        RelatorioCampanhasUseCase campanhas, ZoneId fuso, PainelDoGestorUseCase painel,
                        GerenciarOperadoresUseCase operadores, ClienteRepositorio clientes,
                        GerenciarDepartamentosUseCase departamentos) {
        this.departamentos = departamentos;
        this.clientes = clientes;
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
                             @RequestParam(required = false) UUID operatorId,
                             @RequestParam(required = false) UUID departmentId) {
        cliente.exigirGestor();
        var p = painel.emitir(cliente.id(), periodo(from, to), operatorId, departmentId);
        Map<UUID, String> setores = new HashMap<>();
        departamentos.listar(cliente.id()).forEach(d -> setores.put(d.id(), d.nome()));
        List<DepartmentCount> porDepartamento = p.porDepartamento().entrySet().stream()
                .map(e -> e.getKey().equals(PainelDoGestorUseCase.SEM_RESPONSAVEL)
                        ? new DepartmentCount(null, "Sem departamento", e.getValue())
                        : new DepartmentCount(e.getKey(), setores.getOrDefault(e.getKey(), "?"), e.getValue()))
                .sorted(java.util.Comparator.comparing(DepartmentCount::attendances).reversed())
                .toList();
        Map<UUID, String> nomes = new HashMap<>();
        operadores.listar(cliente.id()).forEach(o -> nomes.put(o.id(), o.nome()));
        List<OperatorCount> porOperador = p.porOperador().entrySet().stream()
                .map(e -> e.getKey().equals(PainelDoGestorUseCase.SEM_RESPONSAVEL)
                        ? new OperatorCount(null, "Sem responsável", e.getValue())
                        : new OperatorCount(e.getKey(), nomes.getOrDefault(e.getKey(), "?"), e.getValue()))
                .sorted(java.util.Comparator.comparing(OperatorCount::attendances).reversed())
                .toList();
        return new DashboardResponse(from, to, p.atendimentos(), p.abertosAgora(), p.aguardandoAgora(), p.finalizados(),
                p.mediaPrimeiraRespostaSegundos(), p.mediaEsperaSegundos(), p.mediaAtendimentoSegundos(), p.porDia(),
                porOperador, porDepartamento,
                p.topContatos().stream().map(c -> new TopContact(c.nome(), c.telefone(), c.atendimentos())).toList());
    }

    record OperatorCount(UUID operatorId, String name, long attendances) {
    }

    record DepartmentCount(UUID departmentId, String name, long attendances) {
    }

    record TopContact(String name, String phone, long attendances) {
    }

    record DashboardResponse(LocalDate from, LocalDate to, long attendances, long openNow, long waitingNow, long finished,
                             Long averageFirstResponseSeconds, Long averageWaitSeconds,
                             Long averageServiceSeconds, Map<LocalDate, Long> perDay,
                             List<OperatorCount> perOperator, List<DepartmentCount> perDepartment,
                             List<TopContact> topContacts) {
    }

    @GetMapping("/attendances")
    @Operation(summary = "Atendimentos finalizados: tempo até a primeira resposta, espera até o operador iniciar, tempo de atendimento e mensagens por atendimento. "
            + "O cliente vê os seus; o admin pode filtrar por clientId. Filtros: operatorId, startedBy "
            + "(customer/company) e search (nome ou telefone do contato). format=csv ou pdf")
    ResponseEntity<?> atendimentos(UsuarioAutenticado usuario, @RequestParam(required = false) UUID clientId,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @RequestParam(required = false) UUID operatorId,
                                   @RequestParam(required = false) String startedBy,
                                   @RequestParam(required = false) String search,
                                   @RequestParam(required = false) String format,
                                   @RequestParam(required = false) UUID departmentId) {
        UUID cliente = usuario.administrador() ? clientId : usuario.sessao().id();
        OrigemAtendimento origem = origem(startedBy);
        AttendancesReport r = AttendancesReport.de(atendimentos.emitir(cliente, periodo(from, to),
                new RelatorioAtendimentosUseCase.Filtro(operatorId, origem, search, departmentId)), from, to);
        String nome = "atendimentos-%s-a-%s".formatted(from, to);
        List<String> cabecalho = List.of("Destinatário", "Iniciado por", "Aberto em", "Primeira resposta em",
                "Finalizado em", "Tempo até 1ª resposta (min)", "Tempo de espera (min)", "Tempo de atendimento (min)", "Enviadas", "Recebidas");
        List<List<Object>> linhas = r.attendances().stream().map(a -> java.util.Arrays.<Object>asList(a.recipient(),
                "customer".equals(a.startedBy()) ? "Cliente final" : "Empresa", horario(a.openedAt()),
                a.firstResponseAt() == null ? null : horario(a.firstResponseAt()), horario(a.finishedAt()),
                minutos(a.firstResponseSeconds()), minutos(a.waitSeconds()), minutos(a.serviceSeconds()), a.messagesSent(),
                a.messagesReceived())).toList();
        if (pdf(format)) {
            List<String> filtros = new ArrayList<>();
            if (operatorId != null && cliente != null) {
                filtros.add("Operador: " + operadores.buscar(cliente, operatorId).nome());
            }
            if (origem != null) {
                filtros.add("Iniciado por: " + (origem == OrigemAtendimento.CLIENTE_FINAL ? "cliente final" : "empresa"));
            }
            adicionarBusca(filtros, "Contato", search);
            return Pdf.arquivo(nome + ".pdf", new Pdf.Relatorio("Relatório de atendimentos finalizados",
                    empresa(cliente), from, to, agora(), filtros, List.of(
                    new Pdf.Indicador("Finalizados", String.valueOf(r.total())),
                    new Pdf.Indicador("Iniciados pelo cliente final", String.valueOf(r.startedByCustomer())),
                    new Pdf.Indicador("1ª resposta (média)", duracao(r.averageFirstResponseSeconds())),
                    new Pdf.Indicador("Espera até iniciar (média)", duracao(r.averageWaitSeconds())),
                    new Pdf.Indicador("Tempo de atendimento (média)", duracao(r.averageServiceSeconds())),
                    new Pdf.Indicador("Mensagens por atendimento", decimal(r.averageMessages()))),
                    List.of("Contato", "Iniciado por", "Aberto em", "1ª resposta em", "Finalizado em",
                            "1ª resposta (min)", "Espera (min)", "Atendimento (min)", "Enviadas", "Recebidas"),
                    new float[]{2.4f, 1.3f, 1.6f, 1.6f, 1.6f, 1.3f, 1.2f, 1.4f, 1.0f, 1.0f}, linhas,
                    "Nenhum atendimento finalizado no período."));
        }
        return csv(format) ? Csv.arquivo(nome + ".csv", cabecalho, linhas) : ResponseEntity.ok(r);
    }

    @GetMapping("/revenue")
    @Operation(summary = "Faturamento por cliente (admin): ranking por receita líquida, % de urgentes, estornos e ticket "
            + "médio. Filtros: planType (prepaid/postpaid) e search (nome do cliente). format=csv ou pdf")
    ResponseEntity<?> faturamento(AdministradorAutenticado administrador,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                  @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                  @RequestParam(required = false) String planType,
                                  @RequestParam(required = false) String search,
                                  @RequestParam(required = false) String format) {
        TipoPlano plano = vazio(planType) ? null : ApiEnums.plano(planType);
        RevenueReport r = RevenueReport.de(faturamento.emitir(periodo(from, to),
                new RelatorioFaturamentoUseCase.Filtro(plano, search)), from, to);
        String nome = "faturamento-%s-a-%s".formatted(from, to);
        List<String> cabecalho = List.of("Cliente", "Plano", "Mensagens", "Urgentes", "% urgentes", "Receita bruta (R$)",
                "Estornos (R$)", "Receita líquida (R$)", "Ticket médio (R$)", "Participação (%)");
        List<List<Object>> linhas = r.clients().stream().map(c -> List.<Object>of(c.name(),
                "prepaid".equals(c.planType()) ? "Pré-pago" : "Pós-pago", c.messages(), c.urgentMessages(),
                c.urgentPercentage(), c.grossRevenue(), c.refunds(), c.netRevenue(), c.averageTicket(),
                c.share())).toList();
        if (pdf(format)) {
            List<String> filtros = new ArrayList<>();
            if (plano != null) {
                filtros.add("Plano: " + (plano == TipoPlano.PRE_PAGO ? "pré-pago" : "pós-pago"));
            }
            adicionarBusca(filtros, "Cliente", search);
            return Pdf.arquivo(nome + ".pdf", new Pdf.Relatorio("Relatório de faturamento por cliente",
                    "Backoffice · todos os clientes", from, to, agora(), filtros, List.of(
                    new Pdf.Indicador("Mensagens", String.valueOf(r.totalMessages())),
                    new Pdf.Indicador("Receita bruta", reais(r.grossRevenue())),
                    new Pdf.Indicador("Estornos", reais(r.refunds())),
                    new Pdf.Indicador("Receita líquida", reais(r.netRevenue()))),
                    cabecalho, new float[]{3.2f, 1.3f, 1.3f, 1.2f, 1.2f, 1.6f, 1.4f, 1.6f, 1.5f, 1.5f}, linhas,
                    "Sem movimentação no período."));
        }
        return csv(format) ? Csv.arquivo(nome + ".csv", cabecalho, linhas) : ResponseEntity.ok(r);
    }

    @GetMapping("/campaigns")
    @Operation(summary = "Desempenho das campanhas: enviadas, entregues, lidas, falhas, estorno e custo por entregue. "
            + "O cliente vê as suas; o admin pode filtrar por clientId. Filtros: priority (normal/urgent), "
            + "content (media/text) e search (nome da campanha). format=csv ou pdf")
    ResponseEntity<?> campanhas(UsuarioAutenticado usuario, @RequestParam(required = false) UUID clientId,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                @RequestParam(required = false) String priority,
                                @RequestParam(required = false) String content,
                                @RequestParam(required = false) String search,
                                @RequestParam(required = false) String format) {
        UUID cliente = usuario.administrador() ? clientId : usuario.sessao().id();
        Prioridade prioridade = vazio(priority) ? null : ApiEnums.prioridade(priority);
        Boolean comMidia = conteudo(content);
        CampaignsReport r = CampaignsReport.de(campanhas.emitir(cliente, periodo(from, to),
                new RelatorioCampanhasUseCase.Filtro(prioridade, comMidia, search)), from, to);
        String nome = "campanhas-%s-a-%s".formatted(from, to);
        if (pdf(format)) {
            List<String> filtros = new ArrayList<>();
            if (prioridade != null) {
                filtros.add("Prioridade: " + (prioridade == Prioridade.URGENTE ? "urgente" : "normal"));
            }
            if (comMidia != null) {
                filtros.add(comMidia ? "Somente com mídia" : "Somente texto");
            }
            adicionarBusca(filtros, "Campanha", search);
            return Pdf.arquivo(nome + ".pdf", new Pdf.Relatorio("Relatório de desempenho das campanhas",
                    empresa(cliente), from, to, agora(), filtros, List.of(
                    new Pdf.Indicador("Campanhas", String.valueOf(r.campaigns().size())),
                    new Pdf.Indicador("Destinatários", String.valueOf(r.totalRecipients())),
                    new Pdf.Indicador("Custo líquido", reais(r.netCost())),
                    new Pdf.Indicador("Taxa de entrega", percentual(r.deliveryRate())),
                    new Pdf.Indicador("Taxa de leitura", percentual(r.readRate()))),
                    List.of("Campanha", "Prioridade", "Criada em", "Destinatários", "Entregues", "Lidas", "Falhas",
                            "Pendentes", "Custo líquido (R$)", "Entrega (%)", "Leitura (%)", "Custo por entregue (R$)"),
                    new float[]{2.6f, 1.1f, 1.6f, 1.5f, 1.3f, 0.9f, 0.9f, 1.3f, 1.4f, 1.1f, 1.1f, 1.5f},
                    r.campaigns().stream().map(c -> java.util.Arrays.<Object>asList(c.name(),
                            "urgent".equals(c.priority()) ? "Urgente" : "Normal", horario(c.createdAt()), c.recipients(),
                            c.delivered(), c.read(), c.failed(), c.pending(), c.netCost(), c.deliveryRate(),
                            c.readRate(), c.costPerDelivered())).toList(),
                    "Nenhuma campanha no período."));
        }
        if (!csv(format)) {
            return ResponseEntity.ok(r);
        }
        return Csv.arquivo(nome + ".csv",
                List.of("Campanha", "Prioridade", "Criada em", "Destinatários", "Enviadas", "Entregues", "Lidas",
                        "Falhas", "Pendentes", "Custo (R$)", "Estornado (R$)", "Custo líquido (R$)",
                        "Taxa de entrega (%)", "Taxa de leitura (%)", "Custo por entregue (R$)"),
                r.campaigns().stream().map(c -> List.<Object>of(c.name(), c.priority(),
                        horario(c.createdAt()), c.recipients(), c.sent(), c.delivered(), c.read(),
                        c.failed(), c.pending(), c.totalCost(), c.refunded(), c.netCost(), c.deliveryRate(),
                        c.readRate(), c.costPerDelivered() == null ? "" : c.costPerDelivered())).toList());
    }

    private String empresa(UUID cliente) {
        if (cliente == null) {
            return "Backoffice · todos os clientes";
        }
        return clientes.buscarPorId(cliente).map(Cliente::nome).orElse("A&R Chat");
    }

    private LocalDateTime agora() {
        return LocalDateTime.now(fuso).withNano(0);
    }

    private static OrigemAtendimento origem(String valor) {
        if (vazio(valor)) {
            return null;
        }
        return switch (valor) {
            case "customer" -> OrigemAtendimento.CLIENTE_FINAL;
            case "company" -> OrigemAtendimento.EMPRESA;
            default -> throw new RegraDeNegocioException("startedBy deve ser customer ou company");
        };
    }

    private static Boolean conteudo(String valor) {
        if (vazio(valor)) {
            return null;
        }
        return switch (valor) {
            case "media" -> true;
            case "text" -> false;
            default -> throw new RegraDeNegocioException("content deve ser media ou text");
        };
    }

    private static void adicionarBusca(List<String> filtros, String rotulo, String busca) {
        if (!vazio(busca)) {
            filtros.add("%s contém \"%s\"".formatted(rotulo, busca.strip()));
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    private static String duracao(Long segundos) {
        if (segundos == null) {
            return "—";
        }
        return segundos < 3600 ? "%d min %02d s".formatted(segundos / 60, segundos % 60)
                : "%d h %02d min".formatted(segundos / 3600, segundos % 3600 / 60);
    }

    private static String decimal(double valor) {
        return String.format(BRASIL, "%.1f", valor);
    }

    private static String reais(BigDecimal valor) {
        return valor == null ? "—" : String.format(BRASIL, "R$ %,.2f", valor);
    }

    private static String percentual(BigDecimal valor) {
        return valor == null ? "—" : String.format(BRASIL, "%.1f%%", valor);
    }

    private static boolean pdf(String formato) {
        return "pdf".equalsIgnoreCase(formato);
    }

    private Periodo periodo(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new RegraDeNegocioException("A data final não pode ser anterior à data inicial");
        }
        return new Periodo(de.atStartOfDay(fuso).toInstant(), ate.plusDays(1).atStartOfDay(fuso).toInstant());
    }

    private LocalDateTime horario(java.time.Instant instante) {
        return instante.atZone(fuso).toLocalDateTime().withNano(0);
    }

    private static Object minutos(Long segundos) {
        return segundos == null ? "" : java.math.BigDecimal.valueOf(segundos).divide(java.math.BigDecimal.valueOf(60), 1,
                java.math.RoundingMode.HALF_EVEN);
    }

    private static boolean csv(String formato) {
        return "csv".equalsIgnoreCase(formato);
    }
}
