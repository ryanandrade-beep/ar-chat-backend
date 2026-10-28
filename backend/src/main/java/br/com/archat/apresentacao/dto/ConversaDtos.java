package br.com.archat.apresentacao.dto;

import br.com.archat.aplicacao.conversa.ResumoConversa;

import java.time.Instant;
import java.util.UUID;

public final class ConversaDtos {

    private ConversaDtos() {
    }

    public record ConversationResponse(UUID id, UUID recipientId, String recipientName, String recipientPhone,
                                       String recipientType, String status, String lastMessageContent,
                                       Instant lastMessageTime, int unreadCount, UUID operatorId,
                                       String operatorName, UUID departmentId, String departmentName,
                                       boolean waitingIvr) {

        public static ConversationResponse de(ResumoConversa resumo) {
            return de(resumo, java.util.Map.of(), java.util.Map.of());
        }

        public static ConversationResponse de(ResumoConversa resumo, java.util.Map<UUID, String> nomesDosOperadores,
                                              java.util.Map<UUID, String> nomesDosDepartamentos) {
            var c = resumo.conversa();
            var d = resumo.destinatario();
            return new ConversationResponse(c.id(), d.id(), d.nome(), d.telefone(), d.grupo() ? "group" : "contact",
                    ApiEnums.atendimento(c.statusAtendimento()), c.ultimaMensagem(), c.dataUltimaMensagem(), c.naoLidas(),
                    c.operadorId(), c.operadorId() == null ? null : nomesDosOperadores.get(c.operadorId()),
                    c.departamentoId(), c.departamentoId() == null ? null : nomesDosDepartamentos.get(c.departamentoId()),
                    c.aguardandoUra());
        }
    }
}
