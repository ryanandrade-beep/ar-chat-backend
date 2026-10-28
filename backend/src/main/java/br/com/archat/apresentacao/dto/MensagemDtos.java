package br.com.archat.apresentacao.dto;

import br.com.archat.aplicacao.mensagem.EnviarMensagemUseCase.MensagemEnviada;
import br.com.archat.dominio.mensagem.ContatoCompartilhado;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Midia;
import br.com.archat.dominio.mensagem.Remetente;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class MensagemDtos {

    private MensagemDtos() {
    }

    public record SendMessageRequest(UUID conversationId, UUID recipientId,
                                     @Size(max = Mensagem.TAMANHO_MAXIMO) String content, @Valid MediaRequest media,
                                     @NotBlank String priority, String channel, @Valid ContactRequest contact) {
    }

    public record ContactRequest(@NotBlank String name, @NotBlank String phone) {

        public static ContatoCompartilhado paraDominio(ContactRequest c) {
            return c == null ? null : new ContatoCompartilhado(c.name(), c.phone());
        }
    }

    public record ContactResponse(String name, String phone) {

        public static ContactResponse de(ContatoCompartilhado c) {
            return c == null ? null : new ContactResponse(c.nome(), c.telefone());
        }
    }

    public record MediaRequest(@NotBlank String type, @NotBlank @Size(max = Midia.TAMANHO_MAXIMO_URL) String url,
                               @Size(max = 255) String fileName) {

        public static Midia paraDominio(MediaRequest media) {
            return media == null ? null : new Midia(ApiEnums.midia(media.type()), media.url(), media.fileName());
        }
    }

    public record MediaResponse(String type, String url, String fileName) {

        public static MediaResponse de(Midia midia) {
            return midia == null ? null : new MediaResponse(ApiEnums.midia(midia.tipo()), midia.url(), midia.nomeArquivo());
        }
    }

    public record SimulatedReplyRequest(@NotBlank @Size(max = Mensagem.TAMANHO_MAXIMO) String content) {
    }

    public record SendMessageResponse(UUID id, UUID conversationId, String status, Instant timestamp,
                                      Instant estimatedDelivery, BigDecimal cost, BigDecimal currentBalance) {

        public static SendMessageResponse de(MensagemEnviada enviada) {
            Mensagem m = enviada.mensagem();
            return new SendMessageResponse(m.id(), m.conversaId(), ApiEnums.status(m.status()), m.criadaEm(),
                    enviada.previsaoEntrega(), m.custo(), enviada.disponivel());
        }
    }

    public record SentBy(String type) {
    }

    public record MessageResponse(UUID id, UUID conversationId, UUID campaignId, String content, MediaResponse media,
                                  ContactResponse contact, UUID operatorId, boolean internal,
                                  SentBy sentBy, boolean automatic, Instant timestamp,
                                  String priority, String status, BigDecimal cost, int attempts, Instant updatedAt) {

        public static MessageResponse de(Mensagem m) {
            return new MessageResponse(m.id(), m.conversaId(), m.campanhaId(), m.conteudo(), MediaResponse.de(m.midia()),
                    ContactResponse.de(m.contato()), m.operadorId(), m.interna(),
                    new SentBy(m.remetente() == Remetente.CLIENTE ? "client" : "user"), m.automatica(), m.criadaEm(),
                    ApiEnums.prioridade(m.prioridade()), ApiEnums.status(m.status()), m.custo(), m.tentativas(),
                    m.atualizadaEm());
        }
    }

    public record MessageStatusResponse(UUID id, String status, int attempts, Instant updatedAt) {

        public static MessageStatusResponse de(Mensagem m) {
            return new MessageStatusResponse(m.id(), ApiEnums.status(m.status()), m.tentativas(), m.atualizadaEm());
        }
    }
}
