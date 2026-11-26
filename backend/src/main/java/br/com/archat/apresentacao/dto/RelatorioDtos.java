package br.com.archat.apresentacao.dto;

import br.com.archat.aplicacao.monitoramento.MonitorarFilaUseCase.SituacaoFila;
import br.com.archat.aplicacao.relatorio.RelatorioCampanhasUseCase.DesempenhoCampanha;
import br.com.archat.aplicacao.relatorio.RelatorioCampanhasUseCase.RelatorioCampanhas;
import br.com.archat.aplicacao.relatorio.RelatorioAtendimentosUseCase.AtendimentoFinalizado;
import br.com.archat.aplicacao.relatorio.RelatorioAtendimentosUseCase.RelatorioAtendimentos;
import br.com.archat.dominio.atendimento.OrigemAtendimento;
import br.com.archat.aplicacao.relatorio.RelatorioFaturamentoUseCase.Faturamento;
import br.com.archat.aplicacao.relatorio.RelatorioFaturamentoUseCase.FaturamentoDoCliente;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RelatorioDtos {

    private RelatorioDtos() {
    }

    public record AttendanceLine(UUID id, UUID conversationId, String recipient, String startedBy, Instant openedAt,
                                 Instant firstResponseAt, Instant finishedAt, Long firstResponseSeconds,
                                 Long waitSeconds, Long serviceSeconds, int messagesSent, int messagesReceived,
                                 UUID departmentId, String closingNote) {

        static AttendanceLine de(AtendimentoFinalizado f) {
            var a = f.atendimento();
            return new AttendanceLine(a.id(), a.conversaId(), f.destinatario(),
                    a.origem() == OrigemAtendimento.CLIENTE_FINAL ? "customer" : "company", a.abertoEm(),
                    a.primeiraRespostaEm(), a.finalizadoEm(), a.tempoAtePrimeiraResposta().map(Duration::toSeconds).orElse(null),
                    a.tempoDeEspera().map(Duration::toSeconds).orElse(null),
                    a.tempoDeAtendimento().map(Duration::toSeconds).orElse(null), a.mensagensEnviadas(), a.mensagensRecebidas(),
                    f.departamentoId(), a.observacaoDeEncerramento());
        }
    }

    public record AttendancesReport(LocalDate from, LocalDate to, long total, long startedByCustomer,
                                    Long averageFirstResponseSeconds, Long averageWaitSeconds, Long averageServiceSeconds,
                                    double averageMessages, Map<LocalDate, Long> perDay, List<AttendanceLine> attendances) {

        public static AttendancesReport de(RelatorioAtendimentos r, LocalDate de, LocalDate ate) {
            return new AttendancesReport(de, ate, r.total(), r.iniciadosPeloCliente(),
                    r.tempoMedioPrimeiraResposta() == null ? null : r.tempoMedioPrimeiraResposta().toSeconds(),
                    r.esperaMedia() == null ? null : r.esperaMedia().toSeconds(),
                    r.atendimentoMedio() == null ? null : r.atendimentoMedio().toSeconds(),
                    Math.round(r.mediaMensagens() * 10) / 10.0, r.porDia(),
                    r.atendimentos().stream().map(AttendanceLine::de).toList());
        }
    }

    public record RevenueLine(UUID clientId, String name, String planType, long messages, long urgentMessages,
                              BigDecimal urgentPercentage, BigDecimal grossRevenue, BigDecimal refunds,
                              BigDecimal netRevenue, BigDecimal averageTicket, BigDecimal share) {

        static RevenueLine de(FaturamentoDoCliente f) {
            return new RevenueLine(f.clienteId(), f.nome(), ApiEnums.plano(f.tipoPlano()), f.mensagens(), f.urgentes(),
                    f.percentualUrgentes(), f.receitaBruta(), f.estornos(), f.receitaLiquida(), f.ticketMedio(),
                    f.participacao());
        }
    }

    public record RevenueReport(LocalDate from, LocalDate to, long totalMessages, BigDecimal grossRevenue,
                                BigDecimal refunds, BigDecimal netRevenue, List<RevenueLine> clients) {

        public static RevenueReport de(Faturamento f, LocalDate de, LocalDate ate) {
            return new RevenueReport(de, ate, f.totalMensagens(), f.receitaBruta(), f.estornos(), f.receitaLiquida(),
                    f.clientes().stream().map(RevenueLine::de).toList());
        }
    }

    public record CampaignLine(UUID id, String name, String priority, Instant createdAt, int recipients,
                               BigDecimal totalCost, long sent, long delivered, long read, long failed, long pending,
                               BigDecimal refunded, BigDecimal netCost, BigDecimal deliveryRate, BigDecimal readRate,
                               BigDecimal costPerDelivered) {

        static CampaignLine de(DesempenhoCampanha d) {
            var c = d.campanha();
            return new CampaignLine(c.id(), c.nome(), ApiEnums.prioridade(c.prioridade()), c.criadaEm(),
                    c.totalDestinatarios(), c.custoTotal(), d.enviadas(), d.entregues(), d.lidas(), d.falhas(),
                    d.pendentes(), d.valorEstornado(), d.custoLiquido(), d.taxaEntrega(), d.taxaLeitura(),
                    d.custoPorEntregue());
        }
    }

    public record CampaignsReport(LocalDate from, LocalDate to, long totalRecipients, BigDecimal netCost,
                                  BigDecimal deliveryRate, BigDecimal readRate, List<CampaignLine> campaigns) {

        public static CampaignsReport de(RelatorioCampanhas r, LocalDate de, LocalDate ate) {
            return new CampaignsReport(de, ate, r.totalDestinatarios(), r.custoLiquidoTotal(), r.taxaEntregaGeral(),
                    r.taxaLeituraGeral(), r.campanhas().stream().map(CampaignLine::de).toList());
        }
    }

    public record QueueStatus(Instant generatedAt, int pendingUrgent, int pendingNormal, long sent,
                              long temporaryFailures, long definitiveFailures, long averageWaitMs,
                              Map<String, Long> messagesByStatus) {

        public static QueueStatus de(SituacaoFila s) {
            Map<String, Long> porStatus = new LinkedHashMap<>();
            s.mensagensPorStatus().forEach((status, total) -> porStatus.put(ApiEnums.status(status), total));
            var p = s.processamento();
            return new QueueStatus(s.geradoEm(), s.pendentesUrgentes(), s.pendentesNormais(), p.enviadas(),
                    p.falhasTemporarias(), p.falhasDefinitivas(), p.esperaMedia().toMillis(), porStatus);
        }
    }
}
