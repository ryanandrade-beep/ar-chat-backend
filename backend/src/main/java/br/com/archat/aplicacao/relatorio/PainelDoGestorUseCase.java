package br.com.archat.aplicacao.relatorio;

import br.com.archat.aplicacao.porta.AtendimentoRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.atendimento.StatusAtendimento;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dashboard do dono da workspace: atendimentos do período, quantos estão abertos agora (e quantos aguardam
 * resposta), finalizados, tempo médio até a primeira resposta, atendimentos por dia, por operador e os 10
 * contatos que mais geram atendimento. O filtro por operador usa o responsável atual da conversa.
 */
public class PainelDoGestorUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final ConversaRepositorio conversas;
    private final DestinatarioRepositorio destinatarios;
    private final ZoneId fuso;

    public PainelDoGestorUseCase(AtendimentoRepositorio atendimentos, ConversaRepositorio conversas,
                                 DestinatarioRepositorio destinatarios, ZoneId fuso) {
        this.atendimentos = atendimentos;
        this.conversas = conversas;
        this.destinatarios = destinatarios;
        this.fuso = fuso;
    }

    public Painel emitir(UUID clienteId, Periodo periodo, UUID operadorId) {
        Map<UUID, Conversa> conversasPorId = conversas.listarPorCliente(clienteId).stream()
                .collect(Collectors.toMap(Conversa::id, Function.identity()));
        List<Atendimento> doPeriodo = atendimentos.listarAbertosNoPeriodo(clienteId, periodo.de(), periodo.ate()).stream()
                .filter(a -> operadorId == null || operadorId.equals(responsavel(conversasPorId, a)))
                .toList();
        List<Conversa> abertasAgora = conversasPorId.values().stream()
                .filter(c -> c.statusAtendimento() != StatusAtendimento.FINALIZADO)
                .filter(c -> operadorId == null || operadorId.equals(c.operadorId()))
                .toList();

        long finalizados = doPeriodo.stream().filter(a -> a.status() == StatusAtendimento.FINALIZADO).count();
        Long mediaPrimeiraResposta = media(doPeriodo.stream().map(Atendimento::tempoAtePrimeiraResposta)
                .flatMap(Optional::stream).toList());

        Map<LocalDate, Long> porDia = new TreeMap<>(doPeriodo.stream()
                .collect(Collectors.groupingBy(a -> LocalDate.ofInstant(a.abertoEm(), fuso), Collectors.counting())));

        Map<UUID, Long> porOperador = doPeriodo.stream().collect(Collectors.groupingBy(
                a -> Optional.ofNullable(responsavel(conversasPorId, a)).orElse(SEM_RESPONSAVEL), Collectors.counting()));

        Map<UUID, Long> porConversa = doPeriodo.stream()
                .collect(Collectors.groupingBy(Atendimento::conversaId, Collectors.counting()));
        Map<UUID, Destinatario> contatos = destinatarios.buscarPorIds(porConversa.keySet().stream()
                        .map(conversasPorId::get).filter(Objects::nonNull).map(Conversa::destinatarioId).toList()).stream()
                .collect(Collectors.toMap(Destinatario::id, Function.identity()));
        List<ContatoFrequente> top = porConversa.entrySet().stream()
                .sorted(Map.Entry.<UUID, Long>comparingByValue().reversed())
                .limit(10)
                .map(e -> {
                    Destinatario d = contatos.get(conversasPorId.get(e.getKey()).destinatarioId());
                    return new ContatoFrequente(d == null ? "?" : d.nome(), d == null ? "" : d.telefone(), e.getValue());
                })
                .toList();

        return new Painel(doPeriodo.size(), abertasAgora.size(),
                abertasAgora.stream().filter(c -> c.statusAtendimento() == StatusAtendimento.AGUARDANDO).count(),
                finalizados, mediaPrimeiraResposta, porDia, porOperador, top);
    }

    public static final UUID SEM_RESPONSAVEL = new UUID(0, 0);

    private static UUID responsavel(Map<UUID, Conversa> conversas, Atendimento a) {
        Conversa c = conversas.get(a.conversaId());
        return c == null ? null : c.operadorId();
    }

    private static Long media(List<Duration> duracoes) {
        return duracoes.isEmpty() ? null
                : Math.round(duracoes.stream().mapToLong(Duration::toSeconds).average().orElse(0));
    }

    public record ContatoFrequente(String nome, String telefone, long atendimentos) {
    }

    public record Painel(long atendimentos, long abertosAgora, long aguardandoAgora, long finalizados,
                         Long mediaPrimeiraRespostaSegundos, Map<LocalDate, Long> porDia, Map<UUID, Long> porOperador,
                         List<ContatoFrequente> topContatos) {
    }
}
