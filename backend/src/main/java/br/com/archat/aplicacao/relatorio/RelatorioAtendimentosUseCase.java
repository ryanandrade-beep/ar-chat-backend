package br.com.archat.aplicacao.relatorio;

import br.com.archat.aplicacao.porta.AtendimentoRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.atendimento.OrigemAtendimento;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Atendimentos finalizados no período: quem foi atendido, quanto tempo a empresa levou para responder
 * pela primeira vez, quanto o cliente esperou até o operador iniciar e quanto durou o atendimento em si, com médias e a quantidade por dia.
 */
public class RelatorioAtendimentosUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final ConversaRepositorio conversas;
    private final DestinatarioRepositorio destinatarios;
    private final ZoneId fuso;

    public RelatorioAtendimentosUseCase(AtendimentoRepositorio atendimentos, ConversaRepositorio conversas,
                                        DestinatarioRepositorio destinatarios, ZoneId fuso) {
        this.atendimentos = atendimentos;
        this.conversas = conversas;
        this.destinatarios = destinatarios;
        this.fuso = fuso;
    }

    public RelatorioAtendimentos emitir(UUID clienteId, Periodo periodo) {
        return emitir(clienteId, periodo, Filtro.NENHUM);
    }

    public RelatorioAtendimentos emitir(UUID clienteId, Periodo periodo, Filtro filtro) {
        List<Atendimento> doPeriodo = atendimentos.listarFinalizados(clienteId, periodo.de(), periodo.ate());
        Map<UUID, Conversa> conversasPorId = conversasDos(doPeriodo);
        Map<UUID, Destinatario> destinatarioPorConversa = destinatariosDasConversas(conversasPorId);
        List<Atendimento> finalizados = doPeriodo.stream()
                .filter(a -> filtro.origem() == null || a.origem() == filtro.origem())
                .filter(a -> filtro.operadorId() == null || Optional.ofNullable(conversasPorId.get(a.conversaId()))
                        .map(c -> filtro.operadorId().equals(c.operadorId())).orElse(false))
                .filter(a -> filtro.departamentoId() == null || Optional.ofNullable(conversasPorId.get(a.conversaId()))
                        .map(c -> filtro.departamentoId().equals(c.departamentoId())).orElse(false))
                .filter(a -> contem(Optional.ofNullable(destinatarioPorConversa.get(a.conversaId()))
                        .map(d -> d.nome() + " " + d.telefone()).orElse(""), filtro.busca()))
                .toList();

        List<AtendimentoFinalizado> linhas = finalizados.stream()
                .map(a -> new AtendimentoFinalizado(a, Optional.ofNullable(destinatarioPorConversa.get(a.conversaId()))
                        .map(Destinatario::nome).orElse("(contato excluído)"),
                        Optional.ofNullable(conversasPorId.get(a.conversaId())).map(Conversa::departamentoId).orElse(null)))
                .toList();
        Map<LocalDate, Long> porDia = finalizados.stream().collect(Collectors.groupingBy(
                a -> LocalDate.ofInstant(a.finalizadoEm(), fuso), TreeMap::new, Collectors.counting()));
        long iniciadosPeloCliente = finalizados.stream()
                .filter(a -> a.origem() == OrigemAtendimento.CLIENTE_FINAL).count();

        return new RelatorioAtendimentos(periodo, linhas, finalizados.size(), iniciadosPeloCliente,
                media(finalizados.stream().map(Atendimento::tempoAtePrimeiraResposta)),
                media(finalizados.stream().map(Atendimento::tempoDeEspera)),
                media(finalizados.stream().map(Atendimento::tempoDeAtendimento)),
                finalizados.stream().mapToInt(a -> a.mensagensEnviadas() + a.mensagensRecebidas()).average().orElse(0),
                porDia);
    }

    private Map<UUID, Conversa> conversasDos(List<Atendimento> lista) {
        return lista.stream().map(Atendimento::conversaId).distinct()
                .map(conversas::buscarPorId).flatMap(Optional::stream)
                .collect(Collectors.toMap(Conversa::id, Function.identity()));
    }

    private Map<UUID, Destinatario> destinatariosDasConversas(Map<UUID, Conversa> conversasPorId) {
        Map<UUID, Destinatario> porId = destinatarios.buscarPorIds(conversasPorId.values().stream()
                        .map(Conversa::destinatarioId).toList()).stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Destinatario::id, Function.identity()));
        return conversasPorId.values().stream().filter(c -> porId.containsKey(c.destinatarioId()))
                .collect(Collectors.toMap(Conversa::id, c -> porId.get(c.destinatarioId())));
    }

    private static Duration media(java.util.stream.Stream<Optional<Duration>> duracoes) {
        List<Duration> validas = duracoes.flatMap(Optional::stream).toList();
        return validas.isEmpty() ? null
                : Duration.ofSeconds(Math.round(validas.stream().mapToLong(Duration::toSeconds).average().orElse(0)));
    }

    static boolean contem(String texto, String busca) {
        return busca == null || busca.isBlank()
                || texto.toLowerCase(Locale.ROOT).contains(busca.strip().toLowerCase(Locale.ROOT));
    }

    public record Filtro(UUID operadorId, OrigemAtendimento origem, String busca, UUID departamentoId) {

        public static final Filtro NENHUM = new Filtro(null, null, null, null);

        public Filtro(UUID operadorId, OrigemAtendimento origem, String busca) {
            this(operadorId, origem, busca, null);
        }
    }

    public record AtendimentoFinalizado(Atendimento atendimento, String destinatario, UUID departamentoId) {
    }

    public record RelatorioAtendimentos(Periodo periodo, List<AtendimentoFinalizado> atendimentos, long total,
                                        long iniciadosPeloCliente, Duration tempoMedioPrimeiraResposta,
                                        Duration esperaMedia, Duration atendimentoMedio, double mediaMensagens, Map<LocalDate, Long> porDia) {
    }
}
