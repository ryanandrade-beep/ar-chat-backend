package br.com.archat.aplicacao.porta;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Como uma campanha foi programada: horário de início, pausa entre mensagens e etiqueta usada no público. */
public interface EnvioDeCampanhas {

    void salvar(Envio envio);

    Map<UUID, Envio> buscar(Collection<UUID> campanhaIds);

    record Envio(UUID campanhaId, Instant agendadaPara, int intervaloSegundos, String etiqueta) {
    }
}
