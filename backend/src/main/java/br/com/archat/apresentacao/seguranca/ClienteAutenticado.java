package br.com.archat.apresentacao.seguranca;

import br.com.archat.aplicacao.excecao.AcessoNegadoException;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Parâmetro de controller que exige alguém de uma workspace: o dono ({@code operadorId} nulo) ou um operador.
 * {@code id} é sempre o cliente (workspace), para as rotas de atendimento servirem aos dois.
 * {@link #atendenteId()} é quem atende: o próprio operador ou o perfil de atendente do dono.
 */
public record ClienteAutenticado(UUID id, UUID operadorId, Supplier<UUID> perfilDoDono) {

    public ClienteAutenticado(UUID id) {
        this(id, null, () -> null);
    }

    public ClienteAutenticado(UUID id, UUID operadorId) {
        this(id, operadorId, () -> null);
    }

    public boolean operador() {
        return operadorId != null;
    }

    public UUID atendenteId() {
        return operadorId != null ? operadorId : perfilDoDono.get();
    }

    public void exigirGestor() {
        if (operador()) {
            throw new AcessoNegadoException("Disponível apenas para o responsável pela conta");
        }
    }
}
