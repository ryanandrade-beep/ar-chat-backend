package br.com.archat.infraestrutura.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/** Configurações da aplicação, lidas do prefixo {@code archat} (application.yml / variáveis de ambiente). */
@ConfigurationProperties("archat")
public record ArChatPropriedades(Token token, AdministradorInicial administradorInicial, Fila fila,
                              EnvioSimulado envioSimulado, ZApi zapi, Webhook webhook,
                              List<String> origensPermitidas) {

    public record Token(String segredo, Duration validade) {
    }

    public record AdministradorInicial(String nome, String login, String senha) {
    }

    public record Fila(int workers, int maximoTentativas, int urgentesSeguidas, Duration tempoEstimadoPorMensagem,
                       Duration atrasoConfirmacaoEntrega, Duration intervaloEntreEnvios) {
    }

    public record EnvioSimulado(Duration atrasoMinimo, Duration atrasoMaximo, double taxaFalha) {
    }

    public record Webhook(String segredo, String urlPublica) {
    }

    public record ZApi(String urlBase, String instancia, String token, String clientToken, Duration timeout) {
    }
}
