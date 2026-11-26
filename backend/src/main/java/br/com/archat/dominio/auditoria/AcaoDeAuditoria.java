package br.com.archat.dominio.auditoria;

import java.util.Arrays;
import java.util.Optional;

/** Ações que ficam registradas na trilha de auditoria da empresa: quem fez o quê e quando. */
public enum AcaoDeAuditoria {

    LOGIN("auth.login", "Entrou no sistema"),
    OPERADOR_CRIADO("operator.created", "Operador cadastrado"),
    OPERADOR_ALTERADO("operator.updated", "Operador alterado"),
    OPERADOR_SENHA("operator.password_changed", "Senha de operador redefinida"),
    DEPARTAMENTO_CRIADO("department.created", "Departamento criado"),
    DEPARTAMENTO_ALTERADO("department.updated", "Departamento alterado"),
    DEPARTAMENTO_OPERADORES("department.operators_changed", "Operadores do departamento alterados"),
    URA_ALTERADA("ivr.updated", "URA alterada"),
    ATENDIMENTO_ASSUMIDO("attendance.assigned", "Atendimento assumido"),
    ATENDIMENTO_TRANSFERIDO("attendance.transferred", "Atendimento transferido"),
    ATENDIMENTO_FINALIZADO("attendance.finished", "Atendimento finalizado"),
    ATENDIMENTO_ENVIADO_GERAL("attendance.sent_to_general", "Atendimento enviado para o geral"),
    ENTREGA_CRIADA("delivery.created", "Entrega gerada"),
    ENTREGA_SAIU("delivery.dispatched", "Entrega saiu"),
    ENTREGA_CONCLUIDA("delivery.delivered", "Entrega concluída"),
    ENTREGA_CANCELADA("delivery.canceled", "Entrega cancelada"),
    ENTREGA_EDITADA("delivery.updated", "Entrega editada"),
    ENTREGA_COBRANCA("delivery.charged", "Cobrança enviada"),
    ENTREGA_CONFIGURACAO("delivery.settings_changed", "Avisos e Pix das entregas alterados"),
    CAMPANHA_DISPARADA("campaign.sent", "Campanha disparada"),
    INTEGRACAO_ALTERADA("integration.updated", "Integração alterada"),
    INTEGRACAO_SEGREDO("integration.secret_regenerated", "Segredo da integração renovado"),
    INTEGRACAO_TESTE("integration.tested", "Integração testada");

    private final String codigo;
    private final String descricao;

    AcaoDeAuditoria(String codigo, String descricao) {
        this.codigo = codigo;
        this.descricao = descricao;
    }

    public String codigo() {
        return codigo;
    }

    public String descricao() {
        return descricao;
    }

    public static Optional<AcaoDeAuditoria> porCodigo(String codigo) {
        return Arrays.stream(values()).filter(a -> a.codigo.equals(codigo)).findFirst();
    }
}
