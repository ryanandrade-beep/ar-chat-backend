package br.com.archat.infraestrutura.envio.zapi;

import br.com.archat.infraestrutura.config.ArChatPropriedades;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Cliente HTTP da Z-API (https://developer.z-api.io). Todas as rotas seguem
 * {@code /instances/{instancia}/token/{token}/...} com o cabeçalho {@code Client-Token}.
 */
@Component
@ConditionalOnProperty(name = "archat.envio.provedor", havingValue = "zapi")
class ClienteZApi {

    private final RestClient http;

    ClienteZApi(ArChatPropriedades propriedades) {
        ArChatPropriedades.ZApi zapi = propriedades.zapi();
        if (vazio(zapi.clientToken())) {
            throw new IllegalStateException("Com archat.envio.provedor=zapi, informe ARCHAT_ZAPI_CLIENT_TOKEN (token da conta Z-API)");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(zapi.timeout());
        fabrica.setReadTimeout(zapi.timeout());
        this.http = RestClient.builder()
                .baseUrl(zapi.urlBase())
                .defaultHeader("Client-Token", zapi.clientToken())
                .requestFactory(fabrica)
                .build();
    }

    RespostaEnvio enviarTexto(Credenciais c, String telefone, String mensagem) {
        return http.post().uri(c.prefixo() + "/send-text").contentType(MediaType.APPLICATION_JSON)
                .body(new EnvioTexto(telefone, mensagem))
                .retrieve().body(RespostaEnvio.class);
    }

    RespostaEnvio enviarImagem(Credenciais c, String telefone, String link, String legenda) {
        return post(c, "/send-image", new EnvioImagem(telefone, link, legenda));
    }

    RespostaEnvio enviarDocumento(Credenciais c, String telefone, String link, String extensao, String nomeArquivo, String legenda) {
        return http.post().uri(c.prefixo() + "/send-document/{extensao}", extensao).contentType(MediaType.APPLICATION_JSON)
                .body(new EnvioDocumento(telefone, link, nomeArquivo, legenda))
                .retrieve().body(RespostaEnvio.class);
    }

    RespostaEnvio enviarAudio(Credenciais c, String telefone, String link) {
        return post(c, "/send-audio", new EnvioAudio(telefone, link));
    }

    RespostaEnvio enviarVideo(Credenciais c, String telefone, String link, String legenda) {
        return post(c, "/send-video", new EnvioVideo(telefone, link, legenda));
    }

    void lerMensagem(Credenciais c, String telefone, String messageId) {
        http.post().uri(c.prefixo() + "/read-message").contentType(MediaType.APPLICATION_JSON)
                .body(new LeituraMensagem(telefone, messageId))
                .retrieve().toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> codigoPorTelefone(Credenciais c, String telefone) {
        return http.get().uri(c.prefixo() + "/phone-code/{telefone}", telefone).retrieve().body(Map.class);
    }

    private RespostaEnvio post(Credenciais c, String rota, Object corpo) {
        return http.post().uri(c.prefixo() + rota).contentType(MediaType.APPLICATION_JSON).body(corpo)
                .retrieve().body(RespostaEnvio.class);
    }

    RespostaEnvio enviarContato(Credenciais c, String telefone, String nome, String telefoneDoContato) {
        return post(c, "/send-contact", new EnvioContato(telefone, nome, telefoneDoContato));
    }

    RespostaStatus status(Credenciais c) {
        return http.get().uri(c.prefixo() + "/status").retrieve().body(RespostaStatus.class);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> qrCodeImagem(Credenciais c) {
        return http.get().uri(c.prefixo() + "/qr-code/image").retrieve().body(Map.class);
    }

    List<GrupoZApi> grupos(Credenciais c, int pagina, int tamanhoPagina) {
        GrupoZApi[] grupos = http.get().uri(c.prefixo() + "/groups?page={pagina}&pageSize={tamanho}", pagina, tamanhoPagina)
                .retrieve().body(GrupoZApi[].class);
        return grupos == null ? List.of() : List.of(grupos);
    }

    List<ContatoZApi> contatos(Credenciais c, int pagina, int tamanhoPagina) {
        ContatoZApi[] contatos = http.get().uri(c.prefixo() + "/contacts?page={pagina}&pageSize={tamanho}", pagina, tamanhoPagina)
                .retrieve().body(ContatoZApi[].class);
        return contatos == null ? List.of() : List.of(contatos);
    }

    void desconectar(Credenciais c) {
        http.get().uri(c.prefixo() + "/disconnect").retrieve().toBodilessEntity();
    }

    record Credenciais(String instancia, String token) {

        String prefixo() {
            return "/instances/" + instancia + "/token/" + token;
        }
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }

    record EnvioTexto(String phone, String message) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EnvioImagem(String phone, String image, String caption) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EnvioDocumento(String phone, String document, String fileName, String caption) {
    }

    record EnvioAudio(String phone, String audio) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EnvioVideo(String phone, String video, String caption) {
    }

    record EnvioContato(String phone, String contactName, String contactPhone) {
    }

    record LeituraMensagem(String phone, String messageId) {
    }

    record RespostaEnvio(String zaapId, String messageId) {
    }

    record RespostaStatus(Boolean connected, String error, Boolean smartphoneConnected) {
    }

    record GrupoZApi(String phone, String name, Boolean isGroup) {
    }

    record ContatoZApi(String phone, String name, @JsonProperty("short") String curto, String vname, @JsonProperty("notify") String apelido) {
    }
}
