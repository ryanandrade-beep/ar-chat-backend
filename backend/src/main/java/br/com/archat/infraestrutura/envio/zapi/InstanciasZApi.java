package br.com.archat.infraestrutura.envio.zapi;

import br.com.archat.aplicacao.excecao.CanalIndisponivelException;
import br.com.archat.aplicacao.porta.InstanciaWhatsAppRepositorio;
import br.com.archat.infraestrutura.config.ArChatPropriedades;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Escolhe a instância da Z-API de cada chamada: a vinculada à empresa pelo backoffice ou, se ela não tiver
 * uma, a instância padrão da plataforma (ARCHAT_ZAPI_INSTANCIA e ARCHAT_ZAPI_TOKEN no .env).
 */
@Component
@ConditionalOnProperty(name = "archat.envio.provedor", havingValue = "zapi")
class InstanciasZApi {

    private final InstanciaWhatsAppRepositorio instancias;
    private final ArChatPropriedades.ZApi padrao;

    InstanciasZApi(InstanciaWhatsAppRepositorio instancias, ArChatPropriedades propriedades) {
        this.instancias = instancias;
        this.padrao = propriedades.zapi();
    }

    ClienteZApi.Credenciais da(UUID clienteId) {
        if (clienteId != null) {
            var propria = instancias.buscarPorCliente(clienteId);
            if (propria.isPresent()) {
                return new ClienteZApi.Credenciais(propria.get().instancia(), propria.get().token());
            }
        }
        if (vazio(padrao.instancia()) || vazio(padrao.token())) {
            throw new CanalIndisponivelException("O WhatsApp desta empresa ainda não foi configurado; "
                    + "peça ao suporte do A&R Chat para vincular a instância");
        }
        return new ClienteZApi.Credenciais(padrao.instancia(), padrao.token());
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
