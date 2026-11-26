package br.com.archat.infraestrutura.integracao;

import br.com.archat.aplicacao.porta.EnvioDeWebhook;
import br.com.archat.dominio.integracao.ConfiguracaoDeIntegracao;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;

/**
 * POST em JSON para a URL da empresa. Cada envio leva X-ArChat-Signature = "sha256=" + HMAC-SHA256(segredo,
 * "{timestamp}.{corpo}"), mais X-ArChat-Timestamp, X-ArChat-Event e X-ArChat-Delivery. Não segue redirecionamento e
 * recusa nomes que apontam para a rede interna.
 */
@Component
class EnvioDeWebhookHttp implements EnvioDeWebhook {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();

    @Override
    public Resposta enviar(String url, String segredo, String idEnvio, String evento, String corpoJson) {
        try {
            URI uri = URI.create(url);
            for (InetAddress endereco : InetAddress.getAllByName(uri.getHost())) {
                if (ConfiguracaoDeIntegracao.enderecoInterno(endereco)) {
                    return new Resposta(false, null, "A URL aponta para um endereço interno; use um endereço público");
                }
            }
            String timestamp = String.valueOf(System.currentTimeMillis() / 1000);
            HttpRequest requisicao = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "ArChat-Webhook/1.0")
                    .header("X-ArChat-Event", evento)
                    .header("X-ArChat-Delivery", idEnvio)
                    .header("X-ArChat-Timestamp", timestamp)
                    .header("X-ArChat-Signature", "sha256=" + assinar(segredo, timestamp + "." + corpoJson))
                    .POST(HttpRequest.BodyPublishers.ofString(corpoJson, StandardCharsets.UTF_8)).build();
            HttpResponse<String> resposta = http.send(requisicao, HttpResponse.BodyHandlers.ofString());
            int codigo = resposta.statusCode();
            String resumo = "HTTP " + codigo + (resposta.body() == null || resposta.body().isBlank() ? "" : " · " + resposta.body());
            return new Resposta(codigo >= 200 && codigo < 300, codigo, resumo);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Resposta(false, null, "Envio interrompido");
        } catch (Exception e) {
            return new Resposta(false, null, "Não foi possível conectar: " + e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : " · " + e.getMessage()));
        }
    }

    private static String assinar(String segredo, String texto) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(segredo.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(texto.getBytes(StandardCharsets.UTF_8)));
    }
}
