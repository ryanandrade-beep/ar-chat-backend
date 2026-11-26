package br.com.archat.dominio.integracao;

import java.util.Arrays;
import java.util.Optional;

/** Eventos que a empresa pode receber no sistema dela (CRM, ERP, planilha). O código é o que vai no JSON. */
public enum EventoDeIntegracao {
    MENSAGEM_RECEBIDA("message.received", "O cliente final mandou uma mensagem", true),
    ATENDIMENTO_INICIADO("attendance.started", "Um operador começou a atender", true),
    ATENDIMENTO_FINALIZADO("attendance.finished", "O atendimento foi finalizado", true),
    ENTREGA_SAIU("delivery.dispatched", "A entrega saiu para o cliente", true),
    ENTREGA_CONCLUIDA("delivery.delivered", "A entrega foi concluída", true),
    TESTE("test.ping", "Evento de teste enviado pelo botão Testar", false);

    private final String codigo;
    private final String descricao;
    private final boolean assinavel;

    EventoDeIntegracao(String codigo, String descricao, boolean assinavel) {
        this.codigo = codigo;
        this.descricao = descricao;
        this.assinavel = assinavel;
    }

    public String codigo() {
        return codigo;
    }

    public String descricao() {
        return descricao;
    }

    public boolean assinavel() {
        return assinavel;
    }

    public static Optional<EventoDeIntegracao> porCodigo(String codigo) {
        return Arrays.stream(values()).filter(e -> e.codigo.equals(codigo)).findFirst();
    }
}
