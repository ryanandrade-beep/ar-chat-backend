package br.com.archat.infraestrutura.integracao;

import br.com.archat.aplicacao.porta.IntegracaoRepositorio;
import br.com.archat.aplicacao.porta.NotificadorDeIntegracao;
import br.com.archat.dominio.integracao.EnvioDeIntegracao;
import br.com.archat.dominio.integracao.EventoDeIntegracao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Monta o envelope padrão do A&R Chat e o grava como pendente, na mesma transação do fato (quem chama já está dentro
 * da unidade de trabalho). O envio acontece depois, em segundo plano. Nada do provedor de WhatsApp vai no envelope.
 */
@Component
class NotificadorDeIntegracaoJdbc implements NotificadorDeIntegracao {
    private static final Logger log = LoggerFactory.getLogger(NotificadorDeIntegracaoJdbc.class);

    private final IntegracaoRepositorio integracoes;
    private final JsonMapper json;
    private final Clock relogio;

    NotificadorDeIntegracaoJdbc(IntegracaoRepositorio integracoes, JsonMapper json, Clock relogio) {
        this.integracoes = integracoes;
        this.json = json;
        this.relogio = relogio;
    }

    @Override
    public EnvioDeIntegracao enfileirar(UUID clienteId, EventoDeIntegracao evento, Map<String, Object> dados, boolean forcar) {
        try {
            if (clienteId == null) {
                return null;
            }
            var config = integracoes.buscarConfiguracao(clienteId);
            if (config.isEmpty() || !forcar && !config.get().recebe(evento)) {
                return null;
            }
            UUID id = UUID.randomUUID();
            Map<String, Object> envelope = new LinkedHashMap<>();
            envelope.put("id", id.toString());
            envelope.put("event", evento.codigo());
            envelope.put("occurredAt", relogio.instant().toString());
            envelope.put("data", dados);
            EnvioDeIntegracao envio = EnvioDeIntegracao.novo(id, clienteId, evento.codigo(), json.writeValueAsString(envelope),
                    relogio.instant());
            integracoes.salvar(envio);
            return envio;
        } catch (RuntimeException e) {
            if (forcar) {
                throw e;
            }

            log.warn("Não foi possível enfileirar o evento {} da empresa {}: {}", evento.codigo(), clienteId, e.getMessage());
            return null;
        }
    }
}
