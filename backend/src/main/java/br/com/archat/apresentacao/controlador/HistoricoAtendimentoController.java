package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.atendimento.ConsultarHistoricoDoAtendimentoUseCase;
import br.com.archat.aplicacao.atendimento.ConsultarHistoricoDoAtendimentoUseCase.Historico;
import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.apresentacao.dto.Csv;
import br.com.archat.apresentacao.dto.Pdf;
import br.com.archat.apresentacao.dto.Xlsx;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.atendimento.OrigemAtendimento;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Remetente;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Histórico de um atendimento (ticket): a conversa trocada entre a abertura e a finalização, com a linha do tempo
 * de espera, primeira resposta e atendimento, e a exportação dessa conversa em PDF, TXT, CSV ou Excel.
 */
@RestController
@RequestMapping("/attendances")
@Tag(name = "Histórico do atendimento", description = "Conversa de um ticket e sua exportação")
class HistoricoAtendimentoController {

    private static final DateTimeFormatter DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ConsultarHistoricoDoAtendimentoUseCase historico;
    private final GerenciarOperadoresUseCase operadores;
    private final ClienteRepositorio clientes;
    private final ZoneId fuso;

    HistoricoAtendimentoController(ConsultarHistoricoDoAtendimentoUseCase historico, GerenciarOperadoresUseCase operadores,
                                   ClienteRepositorio clientes, ZoneId fuso) {
        this.historico = historico;
        this.operadores = operadores;
        this.clientes = clientes;
        this.fuso = fuso;
    }

    record Linha(UUID id, String kind, String author, String content, String mediaType, String fileName, Instant timestamp,
                 String status) {
    }

    record HistoryResponse(UUID id, UUID conversationId, String recipient, String recipientPhone, String startedBy,
                           String status, String operatorName, Instant openedAt, Instant startedAt, Instant firstResponseAt,
                           Instant finishedAt, Long waitSeconds, Long firstResponseSeconds, Long serviceSeconds,
                           int messagesSent, int messagesReceived, String closingNote, List<Linha> messages) {
    }

    @GetMapping("/{id}")
    @Operation(summary = "Histórico do atendimento: as mensagens do ticket (inclui notas internas) e os tempos de espera, "
            + "primeira resposta e atendimento")
    HistoryResponse buscar(ClienteAutenticado cliente, @PathVariable UUID id) {
        Historico h = historico.buscar(cliente.id(), id);
        Map<UUID, String> nomes = nomesDosOperadores(cliente.id());
        Atendimento a = h.atendimento();
        String contato = h.destinatario() == null ? "(contato excluído)" : h.destinatario().nome();
        String telefone = h.destinatario() == null ? "" : h.destinatario().telefone();
        return new HistoryResponse(a.id(), a.conversaId(), contato, telefone,
                a.origem() == OrigemAtendimento.CLIENTE_FINAL ? "customer" : "company", a.status().name().toLowerCase(Locale.ROOT),
                h.conversa().operadorId() == null ? null : nomes.get(h.conversa().operadorId()), a.abertoEm(), a.iniciadoEm(),
                a.primeiraRespostaEm(), a.finalizadoEm(), segundos(a.tempoDeEspera()), segundos(a.tempoAtePrimeiraResposta()),
                segundos(a.tempoDeAtendimento()), a.mensagensEnviadas(), a.mensagensRecebidas(), a.observacaoDeEncerramento(),
                linhas(h, nomes, contato));
    }

    @GetMapping("/{id}/export")
    @Operation(summary = "Exporta a conversa do atendimento: format=pdf, txt, csv ou xlsx")
    ResponseEntity<?> exportar(ClienteAutenticado cliente, @PathVariable UUID id,
                               @RequestParam(defaultValue = "pdf") String format) {
        String formato = format.toLowerCase(Locale.ROOT);
        if (!Set.of("pdf", "txt", "csv", "xlsx").contains(formato)) {
            throw new RegraDeNegocioException("format deve ser pdf, txt, csv ou xlsx");
        }
        Historico h = historico.buscar(cliente.id(), id);
        Map<UUID, String> nomes = nomesDosOperadores(cliente.id());
        String contato = h.destinatario() == null ? "(contato excluído)" : h.destinatario().nome();
        List<Linha> linhas = linhas(h, nomes, contato);
        Atendimento a = h.atendimento();
        String nomeBase = "conversa-%s-%s".formatted(
                contato.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}0-9]+", "-").replaceAll("^-|-$", ""),
                a.abertoEm().atZone(fuso).toLocalDate());
        String operador = h.conversa().operadorId() == null ? "—" : nomes.getOrDefault(h.conversa().operadorId(), "—");

        return switch (formato) {
            case "txt" -> texto(nomeBase + ".txt", h, contato, operador, linhas);
            case "csv" -> Csv.arquivo(nomeBase + ".csv", List.of("Data/hora", "Quem", "Mensagem", "Status"), tabela(linhas));
            case "xlsx" -> Xlsx.arquivo(nomeBase + ".xlsx", "Conversa", List.of("Data/hora", "Quem", "Mensagem", "Status"), tabela(linhas));
            default -> Pdf.arquivo(nomeBase + ".pdf", new Pdf.Relatorio("Conversa · " + contato, empresa(cliente.id()),
                    a.abertoEm().atZone(fuso).toLocalDate(),
                    (a.finalizadoEm() == null ? Instant.now() : a.finalizadoEm()).atZone(fuso).toLocalDate(),
                    LocalDateTime.now(fuso).withNano(0),
                    List.of("Atendimento aberto por " + (a.origem() == OrigemAtendimento.CLIENTE_FINAL ? "cliente final" : "empresa"),
                            "Operador: " + operador),
                    List.of(new Pdf.Indicador("Espera até iniciar", duracao(segundos(a.tempoDeEspera()))),
                            new Pdf.Indicador("1ª resposta", duracao(segundos(a.tempoAtePrimeiraResposta()))),
                            new Pdf.Indicador("Tempo de atendimento", duracao(segundos(a.tempoDeAtendimento()))),
                            new Pdf.Indicador("Mensagens", String.valueOf(linhas.size()))),
                    List.of("Data/hora", "Quem", "Mensagem", "Status"), new float[]{1.7f, 2.0f, 6.0f, 1.1f}, tabela(linhas),
                    "Nenhuma mensagem neste atendimento."));
        };
    }

    private List<Linha> linhas(Historico h, Map<UUID, String> nomes, String contato) {
        return h.mensagens().stream().map(m -> {
            String tipo;
            String autor;
            if (m.interna()) {
                tipo = "note";
                autor = "Nota interna · " + nomes.getOrDefault(m.operadorId(), "equipe");
            } else if (m.remetente() == Remetente.DESTINATARIO) {
                tipo = "customer";
                autor = contato;
            } else if (m.campanhaId() != null) {
                tipo = "campaign";
                autor = "Campanha";
            } else {
                tipo = "operator";
                autor = m.operadorId() == null ? (m.automatica() ? "Automático" : "Empresa") : nomes.getOrDefault(m.operadorId(), "Empresa");
            }
            return new Linha(m.id(), tipo, autor, conteudo(m), m.midia() == null ? null : m.midia().tipo().name().toLowerCase(Locale.ROOT),
                    m.midia() == null ? null : m.midia().nomeArquivo(), m.criadaEm(),
                    m.remetente() == Remetente.DESTINATARIO || m.interna() ? "" : status(m));
        }).toList();
    }

    private static String conteudo(Mensagem m) {
        StringBuilder texto = new StringBuilder();
        if (m.midia() != null) {
            texto.append('[').append(rotuloMidia(m.midia().tipo().name())).append(m.midia().nomeArquivo() == null ? "" : ": " + m.midia().nomeArquivo())
                    .append(']');
        }
        if (m.contato() != null) {
            texto.append("[Contato: ").append(m.contato().nome()).append(' ').append(m.contato().telefone()).append(']');
        }
        if (m.conteudo() != null && !m.conteudo().isBlank()) {
            if (!texto.isEmpty()) {
                texto.append(' ');
            }
            texto.append(m.conteudo());
        }
        return texto.toString();
    }

    private static String rotuloMidia(String tipo) {
        return switch (tipo) {
            case "IMAGEM" -> "Imagem";
            case "DOCUMENTO" -> "Documento";
            case "AUDIO" -> "Áudio";
            case "VIDEO" -> "Vídeo";
            default -> tipo;
        };
    }

    private static String status(Mensagem m) {
        return switch (m.status()) {
            case ENFILEIRADA, PROCESSANDO -> "Na fila";
            case ENVIADA -> "Enviada";
            case ENTREGUE -> "Entregue";
            case LIDA -> "Lida";
            case FALHA -> "Falha";
        };
    }

    private List<List<Object>> tabela(List<Linha> linhas) {
        return linhas.stream().map(l -> java.util.Arrays.<Object>asList(
                l.timestamp().atZone(fuso).toLocalDateTime().withNano(0), l.author(), l.content(), l.status())).toList();
    }

    private ResponseEntity<byte[]> texto(String nome, Historico h, String contato, String operador, List<Linha> linhas) {
        Atendimento a = h.atendimento();
        StringBuilder t = new StringBuilder();
        t.append("Conversa com ").append(contato);
        if (h.destinatario() != null) {
            t.append(" (").append(h.destinatario().telefone()).append(')');
        }
        t.append("\nAtendimento de ").append(DATA_HORA.format(a.abertoEm().atZone(fuso)));
        if (a.finalizadoEm() != null) {
            t.append(" a ").append(DATA_HORA.format(a.finalizadoEm().atZone(fuso)));
        } else {
            t.append(" (em andamento)");
        }
        t.append("\nOperador: ").append(operador)
                .append(" · Espera: ").append(duracao(segundos(a.tempoDeEspera())))
                .append(" · 1ª resposta: ").append(duracao(segundos(a.tempoAtePrimeiraResposta())))
                .append(" · Atendimento: ").append(duracao(segundos(a.tempoDeAtendimento())))
                .append('\n');
        if (a.observacaoDeEncerramento() != null) {
            t.append("Observação do encerramento: ").append(a.observacaoDeEncerramento()).append('\n');
        }
        t.append('\n');
        for (Linha l : linhas) {
            t.append('[').append(DATA_HORA.format(l.timestamp().atZone(fuso))).append("] ").append(l.author()).append(": ")
                    .append(l.content()).append('\n');
        }
        if (linhas.isEmpty()) {
            t.append("(nenhuma mensagem neste atendimento)\n");
        }
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(nome, StandardCharsets.UTF_8).build().toString())
                .body(t.toString().getBytes(StandardCharsets.UTF_8));
    }

    private Map<UUID, String> nomesDosOperadores(UUID clienteId) {
        Map<UUID, String> nomes = new HashMap<>();
        for (Operador o : operadores.listar(clienteId)) {
            nomes.put(o.id(), o.nome());
        }
        return nomes;
    }

    private String empresa(UUID cliente) {
        return clientes.buscarPorId(cliente).map(Cliente::nome).orElse("A&R Chat");
    }

    private static Long segundos(java.util.Optional<Duration> duracao) {
        return duracao.map(Duration::toSeconds).orElse(null);
    }

    private static String duracao(Long segundos) {
        if (segundos == null) {
            return "—";
        }
        return segundos < 3600 ? "%d min %02d s".formatted(segundos / 60, segundos % 60)
                : "%d h %02d min".formatted(segundos / 3600, segundos % 3600 / 60);
    }
}
