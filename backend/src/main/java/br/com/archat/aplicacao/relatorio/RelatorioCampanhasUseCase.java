package br.com.archat.aplicacao.relatorio;

import br.com.archat.aplicacao.porta.CampanhaRepositorio;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.dominio.campanha.Campanha;
import br.com.archat.dominio.mensagem.StatusMensagem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Desempenho das campanhas do período: quantas mensagens chegaram ao WhatsApp, foram entregues, lidas
 * ou falharam, quanto foi estornado e quanto custou cada mensagem entregue.
 */
public class RelatorioCampanhasUseCase {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final CampanhaRepositorio campanhas;
    private final MensagemRepositorio mensagens;

    public RelatorioCampanhasUseCase(CampanhaRepositorio campanhas, MensagemRepositorio mensagens) {
        this.campanhas = campanhas;
        this.mensagens = mensagens;
    }

    public RelatorioCampanhas emitir(UUID clienteId, Periodo periodo) {
        List<Campanha> doPeriodo = campanhas.listarPorPeriodo(clienteId, periodo.de(), periodo.ate());
        Map<UUID, Map<StatusMensagem, Long>> contagens =
                mensagens.contarPorStatusDasCampanhas(doPeriodo.stream().map(Campanha::id).toList());
        List<DesempenhoCampanha> linhas = doPeriodo.stream()
                .map(c -> desempenho(c, contagens.getOrDefault(c.id(), Map.of())))
                .toList();
        long destinatarios = linhas.stream().mapToLong(l -> l.campanha().totalDestinatarios()).sum();
        long entregues = linhas.stream().mapToLong(DesempenhoCampanha::entregues).sum();
        long lidas = linhas.stream().mapToLong(DesempenhoCampanha::lidas).sum();
        long finalizadas = linhas.stream().mapToLong(l -> l.campanha().totalDestinatarios() - l.pendentes()).sum();
        BigDecimal custoLiquido = linhas.stream().map(DesempenhoCampanha::custoLiquido).reduce(ZERO, BigDecimal::add);
        return new RelatorioCampanhas(periodo, linhas, destinatarios, custoLiquido,
                percentual(entregues, finalizadas), percentual(lidas, entregues));
    }

    private static DesempenhoCampanha desempenho(Campanha campanha, Map<StatusMensagem, Long> porStatus) {
        long lidas = qtd(porStatus, StatusMensagem.LIDA);
        long entregues = qtd(porStatus, StatusMensagem.ENTREGUE) + lidas;
        long enviadas = qtd(porStatus, StatusMensagem.ENVIADA) + entregues;
        long falhas = qtd(porStatus, StatusMensagem.FALHA);
        long pendentes = qtd(porStatus, StatusMensagem.ENFILEIRADA) + qtd(porStatus, StatusMensagem.PROCESSANDO);
        BigDecimal precoUnitario = campanha.custoTotal().divide(BigDecimal.valueOf(campanha.totalDestinatarios()), 2,
                RoundingMode.HALF_EVEN);
        BigDecimal estornado = precoUnitario.multiply(BigDecimal.valueOf(falhas)).setScale(2);
        BigDecimal custoLiquido = campanha.custoTotal().subtract(estornado);
        BigDecimal custoPorEntregue = entregues == 0 ? null
                : custoLiquido.divide(BigDecimal.valueOf(entregues), 2, RoundingMode.HALF_EVEN);
        return new DesempenhoCampanha(campanha, enviadas, entregues, lidas, falhas, pendentes, estornado, custoLiquido,
                percentual(entregues, campanha.totalDestinatarios() - pendentes), percentual(lidas, entregues),
                custoPorEntregue);
    }

    private static long qtd(Map<StatusMensagem, Long> porStatus, StatusMensagem status) {
        return porStatus.getOrDefault(status, 0L);
    }

    private static BigDecimal percentual(long parte, long todo) {
        return todo <= 0 ? ZERO
                : BigDecimal.valueOf(parte).multiply(CEM).divide(BigDecimal.valueOf(todo), 2, RoundingMode.HALF_EVEN);
    }

    public record DesempenhoCampanha(Campanha campanha, long enviadas, long entregues, long lidas, long falhas,
                                     long pendentes, BigDecimal valorEstornado, BigDecimal custoLiquido,
                                     BigDecimal taxaEntrega, BigDecimal taxaLeitura, BigDecimal custoPorEntregue) {
    }

    public record RelatorioCampanhas(Periodo periodo, List<DesempenhoCampanha> campanhas, long totalDestinatarios,
                                     BigDecimal custoLiquidoTotal, BigDecimal taxaEntregaGeral,
                                     BigDecimal taxaLeituraGeral) {
    }
}
