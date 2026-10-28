package br.com.archat.aplicacao.porta;

import java.util.Set;
import java.util.UUID;

/** Operadores que participam de uma conversa além do responsável. */
public interface ParticipanteRepositorio {

    Set<UUID> participantes(UUID conversaId);

    void adicionar(UUID conversaId, UUID operadorId);

    void remover(UUID conversaId, UUID operadorId);
}
