package br.com.archat.apresentacao.seguranca;

import br.com.archat.aplicacao.excecao.AcessoNegadoException;

import java.util.UUID;

/**
 * Parâmetro de controller que exige alguém de uma workspace: o dono ({@code operadorId} nulo) ou um operador.
 * {@code id} é sempre o cliente (workspace), para as rotas de atendimento servirem aos dois.
 */
public record ClienteAutenticado(UUID id, UUID operadorId) {

    public ClienteAutenticado(UUID id) {
        this(id, null);
    }

    public boolean operador() {
        return operadorId != null;
    }

    public void exigirGestor() {
        if (operador()) {
            throw new AcessoNegadoException("Disponível apenas para o responsável pela conta");
        }
    }
}
