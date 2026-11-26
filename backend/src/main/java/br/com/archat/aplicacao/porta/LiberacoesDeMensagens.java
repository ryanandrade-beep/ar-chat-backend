package br.com.archat.aplicacao.porta;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Mensagens já cobradas que só entram na fila de envio no horário marcado (campanha agendada ou com pausa). */
public interface LiberacoesDeMensagens {

    void agendar(UUID mensagemId, Instant liberarEm);

    List<UUID> vencidas(Instant agora, int limite);

    void remover(UUID mensagemId);

    boolean existe(UUID mensagemId);
}
