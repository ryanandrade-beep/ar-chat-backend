package br.com.archat.apresentacao.dto;

import br.com.archat.aplicacao.campanha.ConsultarCampanhasUseCase.AndamentoCampanha;
import br.com.archat.aplicacao.campanha.DispararCampanhaUseCase.CampanhaDisparada;
import br.com.archat.dominio.campanha.Campanha;
import br.com.archat.dominio.mensagem.Mensagem;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CampanhaDtos {

    private CampanhaDtos() {
    }

    public record CreateCampaignRequest(@NotBlank @Size(max = 120) String name,
                                        @Size(max = Mensagem.TAMANHO_MAXIMO) String content,
                                        @Valid MensagemDtos.MediaRequest media, @NotBlank String priority, List<UUID> recipientIds, Boolean allOptedIn,
                                        String channel, Instant scheduledAt, Integer intervalSeconds, String tag) {

        public boolean todosQueAceitam() {
            return Boolean.TRUE.equals(allOptedIn);
        }
    }

    public record CampaignCreatedResponse(UUID id, String name, String priority, int totalRecipients,
                                          BigDecimal totalCost, BigDecimal currentBalance, Instant createdAt) {

        public static CampaignCreatedResponse de(CampanhaDisparada disparada) {
            Campanha c = disparada.campanha();
            return new CampaignCreatedResponse(c.id(), c.nome(), ApiEnums.prioridade(c.prioridade()),
                    c.totalDestinatarios(), c.custoTotal(), disparada.disponivel(), c.criadaEm());
        }
    }

    public record CampaignResponse(UUID id, String name, String content, MensagemDtos.MediaResponse media,
                                   String priority, int totalRecipients,
                                   BigDecimal totalCost, Instant createdAt, Map<String, Long> messagesByStatus,
                                   Instant scheduledAt, int intervalSeconds, String tag) {

        public static CampaignResponse de(AndamentoCampanha andamento) {
            Campanha c = andamento.campanha();
            Map<String, Long> porStatus = new LinkedHashMap<>();
            andamento.mensagensPorStatus().forEach((status, total) -> porStatus.put(ApiEnums.status(status), total));
            return new CampaignResponse(c.id(), c.nome(), c.conteudo(), MensagemDtos.MediaResponse.de(c.midia()),
                    ApiEnums.prioridade(c.prioridade()),
                    c.totalDestinatarios(), c.custoTotal(), c.criadaEm(), porStatus,
                    andamento.envio() == null ? null : andamento.envio().agendadaPara(),
                    andamento.envio() == null ? 0 : andamento.envio().intervaloSegundos(),
                    andamento.envio() == null ? null : andamento.envio().etiqueta());
        }
    }
}
