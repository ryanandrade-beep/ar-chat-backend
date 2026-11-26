package br.com.archat.apresentacao.seguranca;

import br.com.archat.dominio.auditoria.AcaoDeAuditoria;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/**
 * Deixa o controller complementar o registro de auditoria da requisição em curso (nome do recurso criado, detalhe ou uma
 * ação diferente da padrão da rota). O interceptor grava depois, só se a resposta for de sucesso.
 */
public final class AuditoriaDaRequisicao {

    public static final String ACAO = "archat.auditoria.acao";
    public static final String RECURSO = "archat.auditoria.recurso";
    public static final String DETALHE = "archat.auditoria.detalhe";
    public static final String SESSAO = "archat.auditoria.sessao";

    private AuditoriaDaRequisicao() {
    }

    public static void recurso(String recurso) {
        definir(RECURSO, recurso);
    }

    public static void detalhe(String detalhe) {
        definir(DETALHE, detalhe);
    }

    public static void acao(AcaoDeAuditoria acao) {
        definir(ACAO, acao);
    }

    private static void definir(String chave, Object valor) {
        RequestAttributes atributos = RequestContextHolder.getRequestAttributes();
        if (atributos != null && valor != null) {
            atributos.setAttribute(chave, valor, RequestAttributes.SCOPE_REQUEST);
        }
    }
}
