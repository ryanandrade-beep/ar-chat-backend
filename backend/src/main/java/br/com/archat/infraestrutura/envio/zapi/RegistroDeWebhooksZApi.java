package br.com.archat.infraestrutura.envio.zapi;

import br.com.archat.infraestrutura.config.ArChatPropriedades;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Com ARCHAT_URL_PUBLICA definida, aponta os webhooks de cada instância Z-API (a padrão e as vinculadas às empresas)
 * para este backend a cada partida. Útil com túnel temporário, cujo endereço muda a cada execução.
 */
@Component
@ConditionalOnProperty(name = "archat.envio.provedor", havingValue = "zapi")
class RegistroDeWebhooksZApi implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RegistroDeWebhooksZApi.class);

    private final ClienteZApi zapi;
    private final JdbcTemplate jdbc;
    private final ArChatPropriedades propriedades;

    RegistroDeWebhooksZApi(ClienteZApi zapi, JdbcTemplate jdbc, ArChatPropriedades propriedades) {
        this.zapi = zapi;
        this.jdbc = jdbc;
        this.propriedades = propriedades;
    }

    @Override
    public void run(ApplicationArguments argumentos) {
        String base = propriedades.webhook().urlPublica();
        if (base == null || base.isBlank()) {
            log.info("ARCHAT_URL_PUBLICA não definida: os webhooks da Z-API não foram alterados");
            return;
        }
        String raiz = base.strip().replaceAll("/+$", "") + "/webhooks/zapi/" + propriedades.webhook().segredo();
        Map<String, String> instancias = new LinkedHashMap<>();
        var padrao = propriedades.zapi();
        if (padrao.instancia() != null && !padrao.instancia().isBlank() && padrao.token() != null && !padrao.token().isBlank()) {
            instancias.put(padrao.instancia(), padrao.token());
        }
        jdbc.query("SELECT instancia, token FROM instancia_whatsapp", rs -> {
            instancias.put(rs.getString(1), rs.getString(2));
        });
        instancias.forEach((instancia, token) -> {
            try {
                zapi.registrarWebhooks(new ClienteZApi.Credenciais(instancia, token), raiz + "/received", raiz + "/status");
                log.info("Webhooks da instância {} apontados para {}", instancia, base.strip());
            } catch (RuntimeException e) {
                log.warn("Não foi possível registrar os webhooks da instância {}: {}", instancia, e.getMessage());
            }
        });
    }
}
