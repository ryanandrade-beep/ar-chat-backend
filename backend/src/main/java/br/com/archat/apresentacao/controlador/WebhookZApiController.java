package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.atendimento.ReceberMensagemUseCase;
import br.com.archat.aplicacao.excecao.NaoAutenticadoException;
import br.com.archat.aplicacao.mensagem.AtualizarStatusPeloCanalUseCase;
import br.com.archat.aplicacao.mensagem.AtualizarStatusPeloCanalUseCase.AvisoDoCanal;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.ContatoCompartilhado;
import br.com.archat.dominio.mensagem.Midia;
import br.com.archat.dominio.mensagem.TipoMidia;
import br.com.archat.aplicacao.porta.InstanciaWhatsAppRepositorio;
import br.com.archat.infraestrutura.config.ArChatPropriedades;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Webhooks da Z-API (https://developer.z-api.io/webhooks/introduction), no formato da documentação:
 * "Ao receber" (ReceivedCallback) e "Status da mensagem" (MessageStatusCallback). A Z-API não assina as
 * chamadas, então a URL carrega um segredo; sem ele, a chamada é recusada. Configure na instância:
 * https://SEU-ENDERECO/webhooks/zapi/{segredo}/received e .../status (a Z-API exige HTTPS).
 */
@RestController
@RequestMapping("/webhooks/zapi/{segredo}")
@Tag(name = "Webhooks Z-API", description = "Chamados pela Z-API; não usam token, e sim o segredo na URL")
class WebhookZApiController {

    private static final Logger log = LoggerFactory.getLogger(WebhookZApiController.class);

    private final ReceberMensagemUseCase receber;
    private final AtualizarStatusPeloCanalUseCase atualizarStatus;
    private final byte[] segredo;

    private final InstanciaWhatsAppRepositorio instancias;

    WebhookZApiController(ReceberMensagemUseCase receber, AtualizarStatusPeloCanalUseCase atualizarStatus,
                          ArChatPropriedades propriedades,
                          InstanciaWhatsAppRepositorio instancias) {
        this.instancias = instancias;
        this.receber = receber;
        this.atualizarStatus = atualizarStatus;
        this.segredo = propriedades.webhook().segredo().getBytes(StandardCharsets.UTF_8);
    }

    @PostMapping("/received")
    @Operation(summary = "Ao receber: mensagem enviada por um contato ao número conectado")
    Map<String, Object> recebida(@PathVariable String segredo, @RequestBody RecebidaZApi corpo) {
        validar(segredo);
        if (Boolean.TRUE.equals(corpo.fromMe()) || !"ReceivedCallback".equals(corpo.type())) {
            return Map.of("ignored", true);
        }
        Instant quando = corpo.momment() == null ? null : Instant.ofEpochMilli(corpo.momment());
        UUID empresa = instancias.buscarPorInstancia(corpo.instanceId()).map(i -> i.clienteId()).orElse(null);
        boolean registrada = (corpo.contato() != null
                ? receber.contatoDoCanal(empresa, corpo.phone(), corpo.contato(), corpo.messageId(), quando)
                : receber.doCanal(empresa, corpo.phone(), corpo.textoOuLegenda(), corpo.midia(), corpo.messageId(), quando))
                .isPresent();
        if (!registrada) {
            log.info("Webhook Z-API: mensagem {} de {} ignorada (repetida ou contato não cadastrado)",
                    corpo.messageId(), corpo.phone());
        }
        return Map.of("registered", registrada);
    }

    @PostMapping("/status")
    @Operation(summary = "Status da mensagem: RECEIVED (entregue) e READ/PLAYED (lida)")
    Map<String, Object> status(@PathVariable String segredo, @RequestBody StatusZApi corpo) {
        validar(segredo);
        AvisoDoCanal aviso = switch (corpo.status() == null ? "" : corpo.status()) {
            case "RECEIVED" -> AvisoDoCanal.ENTREGUE;
            case "READ", "PLAYED" -> AvisoDoCanal.LIDA;
            default -> null;
        };
        if (aviso == null || corpo.ids() == null) {
            return Map.of("updated", 0);
        }
        return Map.of("updated", atualizarStatus.aplicar(corpo.ids(), aviso));
    }

    private void validar(String informado) {
        if (!MessageDigest.isEqual(segredo, informado.getBytes(StandardCharsets.UTF_8))) {
            throw new NaoAutenticadoException("Webhook com segredo inválido");
        }
    }

    record RecebidaZApi(String instanceId, String type, String messageId, String phone, Boolean fromMe, Boolean isGroup,
                        Long momment, String senderName, Texto text, Imagem image, Audio audio, Video video,
                        Documento document, Contato contact) {

        ContatoCompartilhado contato() {
            if (contact == null || contact.phones() == null || contact.phones().isEmpty()) return null;
            try {
                return new ContatoCompartilhado(contact.displayName(), contact.phones().get(0));
            } catch (RegraDeNegocioException e) {
                return null;
            }
        }

        record Contato(String displayName, String vCard, List<String> phones) {
        }

        String textoOuLegenda() {
            if (text != null) return text.message();
            if (image != null) return image.caption();
            if (video != null) return video.caption();
            if (document != null) return document.caption();
            return null;
        }

        Midia midia() {
            try {
                if (image != null && image.imageUrl() != null) return new Midia(TipoMidia.IMAGEM, image.imageUrl(), null);
                if (audio != null && audio.audioUrl() != null) return new Midia(TipoMidia.AUDIO, audio.audioUrl(), null);
                if (video != null && video.videoUrl() != null) return new Midia(TipoMidia.VIDEO, video.videoUrl(), null);
                if (document != null && document.documentUrl() != null) {
                    return new Midia(TipoMidia.DOCUMENTO, document.documentUrl(), document.fileName());
                }
            } catch (RegraDeNegocioException e) {
                return null;
            }
            return null;
        }

        record Texto(String message) {
        }

        record Imagem(String imageUrl, String caption, String mimeType) {
        }

        record Audio(String audioUrl, String mimeType) {
        }

        record Video(String videoUrl, String caption, String mimeType) {
        }

        record Documento(String documentUrl, String fileName, String title, String caption, String mimeType) {
        }
    }

    record StatusZApi(String type, String status, List<String> ids, String phone, Boolean isGroup) {
    }
}
