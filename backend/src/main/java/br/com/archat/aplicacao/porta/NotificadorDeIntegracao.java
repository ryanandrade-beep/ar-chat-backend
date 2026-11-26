package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.integracao.EnvioDeIntegracao;
import br.com.archat.dominio.integracao.EventoDeIntegracao;

import java.util.Map;
import java.util.UUID;

/**
 * Avisa o sistema da empresa (CRM etc.) de que algo aconteceu. Só enfileira: o envio é feito em segundo plano,
 * então um CRM fora do ar nunca atrapalha o atendimento.
 */
public interface NotificadorDeIntegracao {
    NotificadorDeIntegracao NENHUM = (cliente, evento, dados, forcar) -> null;

    EnvioDeIntegracao enfileirar(UUID clienteId, EventoDeIntegracao evento, Map<String, Object> dados, boolean forcar);

    default void notificar(UUID clienteId, EventoDeIntegracao evento, Map<String, Object> dados) {
        enfileirar(clienteId, evento, dados, false);
    }
}
