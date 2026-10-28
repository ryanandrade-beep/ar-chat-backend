package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.mensagem.ConfirmarRecebimentoUseCase;
import br.com.archat.aplicacao.mensagem.ConsultarMensagensUseCase;
import br.com.archat.aplicacao.mensagem.EnviarMensagemUseCase;
import br.com.archat.aplicacao.porta.FiltroMensagens;
import br.com.archat.apresentacao.dto.ApiEnums;
import br.com.archat.apresentacao.dto.MensagemDtos.MediaRequest;
import br.com.archat.apresentacao.dto.MensagemDtos.MessageResponse;
import br.com.archat.apresentacao.dto.MensagemDtos.MessageStatusResponse;
import br.com.archat.apresentacao.dto.MensagemDtos.SendMessageRequest;
import br.com.archat.apresentacao.dto.MensagemDtos.SendMessageResponse;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/messages")
@Tag(name = "Mensagens")
class MensagemController {

    private final EnviarMensagemUseCase enviar;
    private final ConsultarMensagensUseCase consultar;
    private final ConfirmarRecebimentoUseCase confirmarRecebimento;

    MensagemController(EnviarMensagemUseCase enviar, ConsultarMensagensUseCase consultar,
                       ConfirmarRecebimentoUseCase confirmarRecebimento) {
        this.enviar = enviar;
        this.consultar = consultar;
        this.confirmarRecebimento = confirmarRecebimento;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Envia uma mensagem de texto ou com mídia (imagem, documento, áudio, vídeo): cobra o valor e coloca na fila (402 se faltar saldo/limite)")
    SendMessageResponse enviar(ClienteAutenticado cliente, @Valid @RequestBody SendMessageRequest r) {
        ApiEnums.exigirCanalDisponivel(r.channel());
        return SendMessageResponse.de(enviar.executar(new EnviarMensagemUseCase.Comando(cliente.id(),
                r.conversationId(), r.recipientId(), r.content(), MediaRequest.paraDominio(r.media()),
                ApiEnums.prioridade(r.priority()), cliente.operadorId())));
    }

    @GetMapping
    @Operation(summary = "Lista mensagens com filtros (status: queued, processing, sent, delivered, read, failed)")
    List<MessageResponse> listar(ClienteAutenticado cliente, @RequestParam(required = false) UUID conversationId,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String priority) {
        FiltroMensagens filtro = new FiltroMensagens(cliente.id(), conversationId,
                status == null ? null : ApiEnums.status(status),
                priority == null ? null : ApiEnums.prioridade(priority));
        return consultar.listar(filtro).stream().map(MessageResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhes de uma mensagem")
    MessageResponse buscar(ClienteAutenticado cliente, @PathVariable UUID id) {
        return MessageResponse.de(consultar.buscar(cliente.id(), id));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Status atual de uma mensagem (usado em polling pelo frontend)")
    MessageStatusResponse status(ClienteAutenticado cliente, @PathVariable UUID id) {
        return MessageStatusResponse.de(consultar.buscar(cliente.id(), id));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Simula a leitura da mensagem pelo destinatário (somente mensagens entregues)")
    MessageStatusResponse marcarComoLida(ClienteAutenticado cliente, @PathVariable UUID id) {
        return MessageStatusResponse.de(confirmarRecebimento.confirmarLeitura(cliente.id(), id));
    }
}
