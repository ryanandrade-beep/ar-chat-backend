package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.departamento.GerenciarDepartamentosUseCase;
import br.com.archat.aplicacao.destinatario.GerenciarDestinatariosUseCase;
import br.com.archat.dominio.financeiro.TipoTransacao;
import br.com.archat.aplicacao.cliente.ConsultarExtratoUseCase;
import br.com.archat.aplicacao.destinatario.GerenciarEtiquetasUseCase;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.relatorio.Periodo;
import br.com.archat.aplicacao.relatorio.RelatorioAtendimentosUseCase;
import br.com.archat.aplicacao.relatorio.RelatorioCampanhasUseCase;
import br.com.archat.apresentacao.dto.ApiEnums;
import br.com.archat.apresentacao.dto.Csv;
import br.com.archat.apresentacao.dto.Pdf;
import br.com.archat.apresentacao.dto.RelatorioDtos.AttendancesReport;
import br.com.archat.apresentacao.dto.RelatorioDtos.CampaignsReport;
import br.com.archat.apresentacao.dto.Xlsx;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.dominio.atendimento.OrigemAtendimento;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Prioridade;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Exportação montada por quem usa: escolhe o conjunto de dados (atendimentos, campanhas ou contatos), os
 * filtros, as colunas e o formato (CSV, Excel ou PDF). O catálogo de conjuntos e colunas vem de {@code GET /exports}.
 */
@RestController
@RequestMapping("/exports")
@Tag(name = "Exportação", description = "Arquivos CSV, Excel e PDF com os filtros e as colunas escolhidos pelo dono da workspace")
class ExportacaoController {

    private static final Locale BRASIL = Locale.forLanguageTag("pt-BR");

    private final RelatorioAtendimentosUseCase atendimentos;
    private final RelatorioCampanhasUseCase campanhas;
    private final GerenciarDestinatariosUseCase destinatarios;
    private final GerenciarEtiquetasUseCase etiquetas;
    private final ClienteRepositorio clientes;
    private final ConsultarExtratoUseCase extrato;
    private final GerenciarDepartamentosUseCase departamentos;
    private final ZoneId fuso;

    ExportacaoController(RelatorioAtendimentosUseCase atendimentos, RelatorioCampanhasUseCase campanhas,
                         GerenciarDestinatariosUseCase destinatarios, GerenciarEtiquetasUseCase etiquetas,
                         ClienteRepositorio clientes, GerenciarDepartamentosUseCase departamentos, ZoneId fuso,
                         ConsultarExtratoUseCase extrato) {
        this.extrato = extrato;
        this.departamentos = departamentos;
        this.atendimentos = atendimentos;
        this.campanhas = campanhas;
        this.destinatarios = destinatarios;
        this.etiquetas = etiquetas;
        this.clientes = clientes;
        this.fuso = fuso;
    }

    record Coluna(String key, String title, float width) {
    }

    record Conjunto(String key, String title, boolean usesPeriod, List<Coluna> columns) {
    }

    private static final List<Conjunto> CATALOGO = List.of(
            new Conjunto("attendances", "Atendimentos finalizados", true, List.of(
                    new Coluna("recipient", "Contato", 2.4f), new Coluna("department", "Departamento", 1.5f),
                    new Coluna("startedBy", "Iniciado por", 1.3f),
                    new Coluna("openedAt", "Aberto em", 1.6f), new Coluna("firstResponseAt", "Primeira resposta em", 1.6f),
                    new Coluna("finishedAt", "Finalizado em", 1.6f), new Coluna("firstResponse", "Tempo até 1ª resposta (min)", 1.3f),
                    new Coluna("wait", "Tempo de espera (min)", 1.2f), new Coluna("service", "Tempo de atendimento (min)", 1.4f),
                    new Coluna("sent", "Enviadas", 1.0f), new Coluna("received", "Recebidas", 1.0f),
                    new Coluna("closingNote", "Observação do encerramento", 2.4f))),
            new Conjunto("campaigns", "Campanhas", true, List.of(
                    new Coluna("name", "Campanha", 2.6f), new Coluna("priority", "Prioridade", 1.1f),
                    new Coluna("createdAt", "Criada em", 1.6f), new Coluna("recipients", "Destinatários", 1.3f),
                    new Coluna("sent", "Enviadas", 1.1f), new Coluna("delivered", "Entregues", 1.2f),
                    new Coluna("read", "Lidas", 0.9f), new Coluna("failed", "Falhas", 0.9f),
                    new Coluna("pending", "Pendentes", 1.1f), new Coluna("totalCost", "Custo (R$)", 1.2f),
                    new Coluna("refunded", "Estornado (R$)", 1.3f), new Coluna("netCost", "Custo líquido (R$)", 1.4f),
                    new Coluna("deliveryRate", "Taxa de entrega (%)", 1.2f), new Coluna("readRate", "Taxa de leitura (%)", 1.2f),
                    new Coluna("costPerDelivered", "Custo por entregue (R$)", 1.5f))),
            new Conjunto("payments", "Histórico de pagamentos", true, List.of(
                    new Coluna("date", "Data", 1.6f), new Coluna("type", "Tipo", 1.8f), new Coluna("amount", "Valor (R$)", 1.3f),
                    new Coluna("balanceAfter", "Saldo após (R$)", 1.4f), new Coluna("code", "Código da operação", 2.6f))),
            new Conjunto("contacts", "Contatos e grupos", false, List.of(
                    new Coluna("name", "Nome", 2.6f), new Coluna("phone", "Telefone / ID do grupo", 2.2f),
                    new Coluna("type", "Tipo", 1.1f), new Coluna("acceptsMarketing", "Aceita ofertas", 1.3f),
                    new Coluna("tags", "Etiquetas", 2.4f))));

    @GetMapping
    @Operation(summary = "Conjuntos de dados que podem ser exportados e as colunas de cada um")
    List<Conjunto> catalogo(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return CATALOGO;
    }

    @GetMapping("/{dataset}")
    @Operation(summary = "Gera o arquivo: format=csv, xlsx ou pdf; columns=chaves separadas por vírgula (vazio = todas). "
            + "Atendimentos: from, to, operatorId, departmentId, startedBy, search. Campanhas: from, to, priority, content, search. "
            + "Contatos: tag, accepts (yes/no), type (contact/group), search")
    ResponseEntity<?> exportar(ClienteAutenticado cliente, @PathVariable String dataset,
                               @RequestParam(defaultValue = "xlsx") String format,
                               @RequestParam(required = false) String columns,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                               @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                               @RequestParam(required = false) UUID operatorId,
                               @RequestParam(required = false) UUID departmentId,
                               @RequestParam(required = false) String startedBy,
                               @RequestParam(required = false) String priority,
                               @RequestParam(required = false) String content,
                               @RequestParam(required = false) String tag,
                               @RequestParam(required = false) String accepts,
                               @RequestParam(required = false) String type,
                               @RequestParam(required = false) String search) {
        cliente.exigirGestor();
        Conjunto conjunto = CATALOGO.stream().filter(c -> c.key().equals(dataset)).findFirst()
                .orElseThrow(() -> new RegraDeNegocioException("Conjunto de dados desconhecido: " + dataset));
        String formato = format == null ? "xlsx" : format.toLowerCase(Locale.ROOT);
        if (!Set.of("csv", "xlsx", "pdf").contains(formato)) {
            throw new RegraDeNegocioException("format deve ser csv, xlsx ou pdf");
        }
        LocalDate hoje = LocalDate.now(fuso);
        LocalDate de = from == null ? hoje : from;
        LocalDate ate = to == null ? hoje : to;
        if (conjunto.usesPeriod() && (from == null || to == null)) {
            throw new RegraDeNegocioException("Informe o período (from e to)");
        }
        List<Coluna> escolhidas = colunasEscolhidas(conjunto, columns);

        List<Map<String, Object>> linhas = new ArrayList<>();
        List<String> filtros = new ArrayList<>();
        List<Pdf.Indicador> indicadores = new ArrayList<>();
        switch (dataset) {
            case "attendances" -> atendimentosDoPeriodo(cliente.id(), de, ate, operatorId, departmentId, startedBy, search, linhas,
                    filtros, indicadores);
            case "campaigns" -> campanhasDoPeriodo(cliente.id(), de, ate, priority, content, search, linhas, filtros, indicadores);
            case "payments" -> pagamentosDoPeriodo(cliente.id(), de, ate, type, linhas, filtros, indicadores);
            default -> contatos(cliente.id(), tag, accepts, type, search, linhas, filtros, indicadores);
        }

        List<String> cabecalho = escolhidas.stream().map(Coluna::title).toList();
        List<List<Object>> tabela = linhas.stream().map(l -> escolhidas.stream().map(c -> l.get(c.key()))
                .collect(java.util.stream.Collectors.<Object>toList())).toList();
        String nome = conjunto.usesPeriod() ? "%s-%s-a-%s".formatted(dataset, de, ate) : "%s-%s".formatted(dataset, hoje);
        return switch (formato) {
            case "csv" -> Csv.arquivo(nome + ".csv", cabecalho, tabela);
            case "xlsx" -> Xlsx.arquivo(nome + ".xlsx", conjunto.title(), cabecalho, tabela);
            default -> {
                float[] larguras = new float[escolhidas.size()];
                for (int i = 0; i < larguras.length; i++) {
                    larguras[i] = escolhidas.get(i).width();
                }
                yield Pdf.arquivo(nome + ".pdf", new Pdf.Relatorio("Exportação · " + conjunto.title(), empresa(cliente.id()),
                        de, ate, LocalDateTime.now(fuso).withNano(0), filtros, indicadores, cabecalho, larguras, tabela,
                        "Nenhum registro para os filtros escolhidos."));
            }
        };
    }

    private static List<Coluna> colunasEscolhidas(Conjunto conjunto, String columns) {
        if (columns == null || columns.isBlank()) {
            return conjunto.columns();
        }
        Set<String> pedidas = Set.of(columns.split("\\s*,\\s*"));
        Set<String> validas = new java.util.HashSet<>();
        conjunto.columns().forEach(c -> validas.add(c.key()));
        for (String chave : pedidas) {
            if (!validas.contains(chave)) {
                throw new RegraDeNegocioException("Coluna desconhecida: " + chave);
            }
        }
        return conjunto.columns().stream().filter(c -> pedidas.contains(c.key())).toList();
    }

    private void atendimentosDoPeriodo(UUID clienteId, LocalDate de, LocalDate ate, UUID operatorId, UUID departmentId,
                                       String startedBy, String search, List<Map<String, Object>> linhas, List<String> filtros,
                                       List<Pdf.Indicador> indicadores) {
        OrigemAtendimento origem = vazio(startedBy) ? null : switch (startedBy) {
            case "customer" -> OrigemAtendimento.CLIENTE_FINAL;
            case "company" -> OrigemAtendimento.EMPRESA;
            default -> throw new RegraDeNegocioException("startedBy deve ser customer ou company");
        };
        AttendancesReport r = AttendancesReport.de(atendimentos.emitir(clienteId, periodo(de, ate),
                new RelatorioAtendimentosUseCase.Filtro(operatorId, origem, search, departmentId)), de, ate);
        Map<UUID, String> setores = new LinkedHashMap<>();
        departamentos.listar(clienteId).forEach(d -> setores.put(d.id(), d.nome()));
        if (origem != null) {
            filtros.add("Iniciado por: " + (origem == OrigemAtendimento.CLIENTE_FINAL ? "cliente final" : "empresa"));
        }
        if (operatorId != null) {
            filtros.add("Operador selecionado");
        }
        if (departmentId != null) {
            filtros.add("Departamento: " + setores.getOrDefault(departmentId, "selecionado"));
        }
        busca(filtros, "Contato", search);
        indicadores.add(new Pdf.Indicador("Finalizados", String.valueOf(r.total())));
        indicadores.add(new Pdf.Indicador("Espera até iniciar (média)", duracao(r.averageWaitSeconds())));
        indicadores.add(new Pdf.Indicador("Tempo de atendimento (média)", duracao(r.averageServiceSeconds())));
        indicadores.add(new Pdf.Indicador("1ª resposta (média)", duracao(r.averageFirstResponseSeconds())));
        r.attendances().forEach(a -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("recipient", a.recipient());
            l.put("department", a.departmentId() == null ? "" : setores.getOrDefault(a.departmentId(), ""));
            l.put("startedBy", "customer".equals(a.startedBy()) ? "Cliente final" : "Empresa");
            l.put("openedAt", horario(a.openedAt()));
            l.put("firstResponseAt", a.firstResponseAt() == null ? null : horario(a.firstResponseAt()));
            l.put("finishedAt", horario(a.finishedAt()));
            l.put("firstResponse", minutos(a.firstResponseSeconds()));
            l.put("wait", minutos(a.waitSeconds()));
            l.put("service", minutos(a.serviceSeconds()));
            l.put("sent", a.messagesSent());
            l.put("received", a.messagesReceived());
            l.put("closingNote", a.closingNote());
            linhas.add(l);
        });
    }

    private void pagamentosDoPeriodo(UUID clienteId, LocalDate de, LocalDate ate, String type, List<Map<String, Object>> linhas,
                                     List<String> filtros, List<Pdf.Indicador> indicadores) {
        TipoTransacao tipo = vazio(type) ? null : switch (type) {
            case "credit" -> TipoTransacao.CREDITO;
            case "monthly_fee" -> TipoTransacao.MENSALIDADE;
            default -> throw new RegraDeNegocioException("type deve ser credit ou monthly_fee");
        };
        Periodo periodo = periodo(de, ate);
        var pagamentos = extrato.pagamentos(clienteId, periodo.de(), periodo.ate(), tipo);
        if (tipo != null) {
            filtros.add("Tipo: " + (tipo == TipoTransacao.CREDITO ? "recargas de crédito" : "mensalidades do plano"));
        }
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (var t : pagamentos) {
            total = total.add(t.valor());
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("date", horario(t.dataHora()));
            l.put("type", t.tipo() == TipoTransacao.CREDITO ? "Recarga de créditos" : "Mensalidade do plano");
            l.put("amount", t.valor());
            l.put("balanceAfter", t.saldoApos());
            l.put("code", t.id().toString());
            linhas.add(l);
        }
        indicadores.add(new Pdf.Indicador("Pagamentos", String.valueOf(pagamentos.size())));
        indicadores.add(new Pdf.Indicador("Total pago", "R$ " + String.format(BRASIL, "%,.2f", total)));
    }

    private void campanhasDoPeriodo(UUID clienteId, LocalDate de, LocalDate ate, String priority, String content,
                                    String search, List<Map<String, Object>> linhas, List<String> filtros,
                                    List<Pdf.Indicador> indicadores) {
        Prioridade prioridade = vazio(priority) ? null : ApiEnums.prioridade(priority);
        Boolean comMidia = vazio(content) ? null : switch (content) {
            case "media" -> true;
            case "text" -> false;
            default -> throw new RegraDeNegocioException("content deve ser media ou text");
        };
        CampaignsReport r = CampaignsReport.de(campanhas.emitir(clienteId, periodo(de, ate),
                new RelatorioCampanhasUseCase.Filtro(prioridade, comMidia, search)), de, ate);
        if (prioridade != null) {
            filtros.add("Prioridade: " + (prioridade == Prioridade.URGENTE ? "urgente" : "normal"));
        }
        if (comMidia != null) {
            filtros.add(comMidia ? "Somente com mídia" : "Somente texto");
        }
        busca(filtros, "Campanha", search);
        indicadores.add(new Pdf.Indicador("Campanhas", String.valueOf(r.campaigns().size())));
        indicadores.add(new Pdf.Indicador("Destinatários", String.valueOf(r.totalRecipients())));
        indicadores.add(new Pdf.Indicador("Custo líquido", reais(r.netCost())));
        indicadores.add(new Pdf.Indicador("Taxa de entrega", percentual(r.deliveryRate())));
        r.campaigns().forEach(c -> {
            Map<String, Object> l = new LinkedHashMap<>();
            l.put("name", c.name());
            l.put("priority", "urgent".equals(c.priority()) ? "Urgente" : "Normal");
            l.put("createdAt", horario(c.createdAt()));
            l.put("recipients", c.recipients());
            l.put("sent", c.sent());
            l.put("delivered", c.delivered());
            l.put("read", c.read());
            l.put("failed", c.failed());
            l.put("pending", c.pending());
            l.put("totalCost", c.totalCost());
            l.put("refunded", c.refunded());
            l.put("netCost", c.netCost());
            l.put("deliveryRate", c.deliveryRate());
            l.put("readRate", c.readRate());
            l.put("costPerDelivered", c.costPerDelivered());
            linhas.add(l);
        });
    }

    private void contatos(UUID clienteId, String tag, String accepts, String type, String search,
                          List<Map<String, Object>> linhas, List<String> filtros, List<Pdf.Indicador> indicadores) {
        String etiqueta = vazio(tag) ? null : GerenciarEtiquetasUseCase.normalizar(tag);
        Boolean aceita = vazio(accepts) ? null : switch (accepts) {
            case "yes" -> true;
            case "no" -> false;
            default -> throw new RegraDeNegocioException("accepts deve ser yes ou no");
        };
        Boolean grupo = vazio(type) ? null : switch (type) {
            case "contact" -> false;
            case "group" -> true;
            default -> throw new RegraDeNegocioException("type deve ser contact ou group");
        };
        Map<UUID, List<String>> porContato = etiquetas.doCliente(clienteId);
        String termo = vazio(search) ? null : search.strip().toLowerCase(BRASIL);
        destinatarios.listar(clienteId).stream()
                .filter(d -> etiqueta == null || porContato.getOrDefault(d.id(), List.of()).contains(etiqueta))
                .filter(d -> aceita == null || d.aceitaOfertas() == aceita)
                .filter(d -> grupo == null || d.grupo() == grupo)
                .filter(d -> termo == null || d.nome().toLowerCase(BRASIL).contains(termo) || d.telefone().contains(termo))
                .forEach(d -> {
                    Map<String, Object> l = new LinkedHashMap<>();
                    l.put("name", d.nome());
                    l.put("phone", d.telefone());
                    l.put("type", d.grupo() ? "Grupo" : "Contato");
                    l.put("acceptsMarketing", d.aceitaOfertas() ? "Sim" : "Não");
                    l.put("tags", String.join(", ", porContato.getOrDefault(d.id(), List.of())));
                    linhas.add(l);
                });
        if (etiqueta != null) {
            filtros.add("Etiqueta: " + etiqueta);
        }
        if (aceita != null) {
            filtros.add(aceita ? "Aceitam ofertas" : "Não aceitam ofertas");
        }
        if (grupo != null) {
            filtros.add(grupo ? "Somente grupos" : "Somente contatos");
        }
        busca(filtros, "Nome ou telefone", search);
        indicadores.add(new Pdf.Indicador("Registros", String.valueOf(linhas.size())));
        indicadores.add(new Pdf.Indicador("Aceitam ofertas",
                String.valueOf(linhas.stream().filter(l -> "Sim".equals(l.get("acceptsMarketing"))).count())));
    }

    private String empresa(UUID cliente) {
        return clientes.buscarPorId(cliente).map(Cliente::nome).orElse("A&R Chat");
    }

    private Periodo periodo(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new RegraDeNegocioException("A data final não pode ser anterior à data inicial");
        }
        return new Periodo(de.atStartOfDay(fuso).toInstant(), ate.plusDays(1).atStartOfDay(fuso).toInstant());
    }

    private LocalDateTime horario(Instant instante) {
        return instante.atZone(fuso).toLocalDateTime().withNano(0);
    }

    private static Object minutos(Long segundos) {
        return segundos == null ? null : BigDecimal.valueOf(segundos).divide(BigDecimal.valueOf(60), 1, RoundingMode.HALF_EVEN);
    }

    private static void busca(List<String> filtros, String rotulo, String busca) {
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

    private static String reais(BigDecimal valor) {
        return valor == null ? "—" : String.format(BRASIL, "R$ %,.2f", valor);
    }

    private static String percentual(BigDecimal valor) {
        return valor == null ? "—" : String.format(BRASIL, "%.1f%%", valor);
    }
}
