package br.com.archat.aplicacao.integracao;

import br.com.archat.aplicacao.porta.IntegracaoRepositorio;
import br.com.archat.aplicacao.porta.NotificadorDeIntegracao;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.integracao.ConfiguracaoDeIntegracao;
import br.com.archat.dominio.integracao.EnvioDeIntegracao;
import br.com.archat.dominio.integracao.EventoDeIntegracao;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Configuração da integração por webhook de saída: URL, eventos, segredo, teste e histórico de envios. */
public class GerenciarIntegracaoUseCase {
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final IntegracaoRepositorio integracoes;
    private final NotificadorDeIntegracao notificador;
    private final DespacharIntegracoesUseCase despachar;
    private final Clock relogio;

    public GerenciarIntegracaoUseCase(IntegracaoRepositorio integracoes, NotificadorDeIntegracao notificador,
                                      DespacharIntegracoesUseCase despachar, Clock relogio) {
        this.integracoes = integracoes;
        this.notificador = notificador;
        this.despachar = despachar;
        this.relogio = relogio;
    }

    public Optional<ConfiguracaoDeIntegracao> obter(UUID clienteId) {
        return integracoes.buscarConfiguracao(clienteId);
    }

    public ConfiguracaoDeIntegracao salvar(UUID clienteId, String url, Set<EventoDeIntegracao> eventos, boolean ativa) {
        var existente = integracoes.buscarConfiguracao(clienteId);
        ConfiguracaoDeIntegracao nova = existente.isPresent()
                ? existente.get().com(url, eventos, ativa, relogio.instant())
                : new ConfiguracaoDeIntegracao(clienteId, url, novoSegredo(), eventos, ativa, relogio.instant());
        integracoes.salvar(nova);
        return nova;
    }

    public ConfiguracaoDeIntegracao regenerarSegredo(UUID clienteId) {
        ConfiguracaoDeIntegracao nova = configuracao(clienteId).comSegredo(novoSegredo(), relogio.instant());
        integracoes.salvar(nova);
        return nova;
    }

    public EnvioDeIntegracao testar(UUID clienteId) {
        configuracao(clienteId);
        EnvioDeIntegracao envio = notificador.enfileirar(clienteId, EventoDeIntegracao.TESTE,
                Map.of("message", "Teste de integração do A&R Chat"), true);
        if (envio == null) {
            throw new RegraDeNegocioException("Não foi possível criar o evento de teste");
        }
        return despachar.tentar(envio, false);
    }

    public List<EnvioDeIntegracao> ultimos(UUID clienteId) {
        return integracoes.ultimos(clienteId, 50);
    }

    public EnvioDeIntegracao reenviar(UUID clienteId, UUID envioId) {
        EnvioDeIntegracao envio = integracoes.buscarEnvio(envioId).filter(e -> e.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Envio"));
        envio.reenviar(relogio.instant());
        integracoes.salvar(envio);
        return envio;
    }

    private ConfiguracaoDeIntegracao configuracao(UUID clienteId) {
        return integracoes.buscarConfiguracao(clienteId)
                .orElseThrow(() -> new RegraDeNegocioException("Configure a URL da integração antes"));
    }

    private static String novoSegredo() {
        byte[] bytes = new byte[24];
        ALEATORIO.nextBytes(bytes);
        return "whsec_" + HexFormat.of().formatHex(bytes);
    }
}
