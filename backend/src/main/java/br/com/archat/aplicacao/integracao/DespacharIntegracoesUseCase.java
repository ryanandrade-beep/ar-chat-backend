package br.com.archat.aplicacao.integracao;

import br.com.archat.aplicacao.porta.EnvioDeWebhook;
import br.com.archat.aplicacao.porta.IntegracaoRepositorio;
import br.com.archat.dominio.integracao.ConfiguracaoDeIntegracao;
import br.com.archat.dominio.integracao.EnvioDeIntegracao;

import java.time.Clock;
import java.util.List;

/** Entrega os avisos pendentes ao sistema da empresa, com novas tentativas quando ele falha. */
public class DespacharIntegracoesUseCase {
    private static final int LOTE = 20;

    private final IntegracaoRepositorio integracoes;
    private final EnvioDeWebhook webhook;
    private final Clock relogio;

    public DespacharIntegracoesUseCase(IntegracaoRepositorio integracoes, EnvioDeWebhook webhook, Clock relogio) {
        this.integracoes = integracoes;
        this.webhook = webhook;
        this.relogio = relogio;
    }

    public int processarPendentes() {
        List<EnvioDeIntegracao> lote = integracoes.pendentes(relogio.instant(), LOTE);
        lote.forEach(e -> tentar(e, true));
        return lote.size();
    }

    public EnvioDeIntegracao tentar(EnvioDeIntegracao envio, boolean novaTentativa) {
        var config = integracoes.buscarConfiguracao(envio.clienteId());
        if (config.isEmpty()) {
            envio.registrarFalha(null, "Integração não configurada", relogio.instant(), false);
        } else {
            ConfiguracaoDeIntegracao c = config.get();
            var r = webhook.enviar(c.url(), c.segredo(), envio.id().toString(), envio.evento(), envio.payload());
            if (r.sucesso()) {
                envio.registrarSucesso(r.httpStatus() == null ? 200 : r.httpStatus(), r.resumo(), relogio.instant());
            } else {
                envio.registrarFalha(r.httpStatus(), r.resumo(), relogio.instant(), novaTentativa);
            }
        }
        integracoes.salvar(envio);
        return envio;
    }
}
