package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.integracao.ConfiguracaoDeIntegracao;
import br.com.archat.dominio.integracao.EnvioDeIntegracao;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface IntegracaoRepositorio {
    Optional<ConfiguracaoDeIntegracao> buscarConfiguracao(UUID clienteId);

    void salvar(ConfiguracaoDeIntegracao configuracao);

    void salvar(EnvioDeIntegracao envio);

    Optional<EnvioDeIntegracao> buscarEnvio(UUID id);

    List<EnvioDeIntegracao> pendentes(Instant agora, int limite);

    List<EnvioDeIntegracao> ultimos(UUID clienteId, int limite);
}
