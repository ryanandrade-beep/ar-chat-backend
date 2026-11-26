package br.com.archat.apresentacao.seguranca;

import br.com.archat.aplicacao.auditoria.RegistrarAuditoriaUseCase;
import br.com.archat.aplicacao.auditoria.RegistrarAuditoriaUseCase.TipoDeRecurso;
import br.com.archat.dominio.auditoria.AcaoDeAuditoria;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;
import java.util.UUID;

/**
 * Registra na auditoria as ações de escrita que deram certo (resposta 2xx), com o autor tirado da sessão. A tabela abaixo
 * diz qual rota vira qual ação; rotas fora dela não são auditadas.
 */
@Component
public class AuditoriaInterceptor implements HandlerInterceptor {
    private record Regra(AcaoDeAuditoria acao, TipoDeRecurso tipo) {
    }

    private static final Map<String, Regra> REGRAS = Map.ofEntries(
            Map.entry("POST /operators", new Regra(AcaoDeAuditoria.OPERADOR_CRIADO, null)),
            Map.entry("PUT /operators/{id}", new Regra(AcaoDeAuditoria.OPERADOR_ALTERADO, TipoDeRecurso.OPERADOR)),
            Map.entry("PUT /operators/{id}/password", new Regra(AcaoDeAuditoria.OPERADOR_SENHA, TipoDeRecurso.OPERADOR)),
            Map.entry("POST /departments", new Regra(AcaoDeAuditoria.DEPARTAMENTO_CRIADO, null)),
            Map.entry("PUT /departments/{id}", new Regra(AcaoDeAuditoria.DEPARTAMENTO_ALTERADO, TipoDeRecurso.DEPARTAMENTO)),
            Map.entry("PUT /departments/{id}/operators", new Regra(AcaoDeAuditoria.DEPARTAMENTO_OPERADORES, TipoDeRecurso.DEPARTAMENTO)),
            Map.entry("PUT /ivr", new Regra(AcaoDeAuditoria.URA_ALTERADA, null)),
            Map.entry("POST /conversations/{id}/assign", new Regra(AcaoDeAuditoria.ATENDIMENTO_ASSUMIDO, TipoDeRecurso.CONVERSA)),
            Map.entry("POST /conversations/{id}/transfer", new Regra(AcaoDeAuditoria.ATENDIMENTO_TRANSFERIDO, TipoDeRecurso.CONVERSA)),
            Map.entry("POST /conversations/{id}/finish", new Regra(AcaoDeAuditoria.ATENDIMENTO_FINALIZADO, TipoDeRecurso.CONVERSA)),
            Map.entry("POST /deliveries/{id}/dispatch", new Regra(AcaoDeAuditoria.ENTREGA_SAIU, TipoDeRecurso.ENTREGA)),
            Map.entry("POST /deliveries/{id}/deliver", new Regra(AcaoDeAuditoria.ENTREGA_CONCLUIDA, TipoDeRecurso.ENTREGA)),
            Map.entry("POST /deliveries/{id}/cancel", new Regra(AcaoDeAuditoria.ENTREGA_CANCELADA, TipoDeRecurso.ENTREGA)),
            Map.entry("POST /deliveries/{id}/charge", new Regra(AcaoDeAuditoria.ENTREGA_COBRANCA, TipoDeRecurso.ENTREGA)),
            Map.entry("PUT /deliveries/{id}", new Regra(AcaoDeAuditoria.ENTREGA_EDITADA, TipoDeRecurso.ENTREGA)),
            Map.entry("PUT /deliveries/settings", new Regra(AcaoDeAuditoria.ENTREGA_CONFIGURACAO, null)),
            Map.entry("POST /campaigns", new Regra(AcaoDeAuditoria.CAMPANHA_DISPARADA, null)),
            Map.entry("PUT /integrations/webhook", new Regra(AcaoDeAuditoria.INTEGRACAO_ALTERADA, null)),
            Map.entry("POST /integrations/webhook/secret", new Regra(AcaoDeAuditoria.INTEGRACAO_SEGREDO, null)),
            Map.entry("POST /integrations/webhook/test", new Regra(AcaoDeAuditoria.INTEGRACAO_TESTE, null)));

    private final RegistrarAuditoriaUseCase auditoria;

    AuditoriaInterceptor(RegistrarAuditoriaUseCase auditoria) {
        this.auditoria = auditoria;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void afterCompletion(HttpServletRequest requisicao, HttpServletResponse resposta, Object handler, Exception erro) {
        try {
            if (erro != null || resposta.getStatus() < 200 || resposta.getStatus() >= 300) {
                return;
            }
            Object padrao = requisicao.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            Regra regra = padrao == null ? null : REGRAS.get(requisicao.getMethod() + " " + padrao);
            if (!(requisicao.getAttribute(AuditoriaDaRequisicao.SESSAO) instanceof ClienteAutenticado ator) || regra == null) {
                return;
            }
            Map<String, String> variaveis = (Map<String, String>) requisicao.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
            UUID recursoId = variaveis == null || !variaveis.containsKey("id") ? null : UUID.fromString(variaveis.get("id"));
            AcaoDeAuditoria acao = requisicao.getAttribute(AuditoriaDaRequisicao.ACAO) instanceof AcaoDeAuditoria a ? a : regra.acao();
            String recurso = requisicao.getAttribute(AuditoriaDaRequisicao.RECURSO) instanceof String s ? s : null;
            String detalhe = requisicao.getAttribute(AuditoriaDaRequisicao.DETALHE) instanceof String s ? s : null;
            auditoria.registrarSobre(ator.id(), ator.atendenteId(), acao, regra.tipo(), recursoId, recurso, detalhe);
        } catch (RuntimeException e) {
        }
    }
}
