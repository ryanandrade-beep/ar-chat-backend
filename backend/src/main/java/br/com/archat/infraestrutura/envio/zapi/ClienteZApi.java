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
        if (vazio(zapi.instancia()) || vazio(zapi.token()) || vazio(zapi.clientToken())) {
            throw new IllegalStateException("Com archat.envio.provedor=zapi, informe ARCHAT_ZAPI_INSTANCIA, ARCHAT_ZAPI_TOKEN e ARCHAT_ZAPI_CLIENT_TOKEN");
        }
        SimpleClientHttpRequestFactory fabrica = new SimpleClientHttpRequestFactory();
        fabrica.setConnectTimeout(zapi.timeout());
        fabrica.setReadTimeout(zapi.timeout());
        this.http = RestClient.builder()
                .baseUrl(zapi.urlBase() + "/instances/" + zapi.instancia() + "/token/" + zapi.token())
                .defaultHeader("Client-Token", zapi.clientToken())
                .requestFactory(fabrica)
                .build();
    }

    RespostaEnvio enviarTexto(String telefone, String mensagem) {
        return http.post().uri("/send-text").contentType(MediaType.APPLICATION_JSON)
                .body(new EnvioTexto(telefone, mensagem))
                .retrieve().body(RespostaEnvio.class);
    }

    RespostaEnvio enviarImagem(String telefone, String link, String legenda) {
        return post("/send-image", new EnvioImagem(telefone, link, legenda));
    }

    RespostaEnvio enviarDocumento(String telefone, String link, String extensao, String nomeArquivo, String legenda) {
        return http.post().uri("/send-document/{extensao}", extensao).contentType(MediaType.APPLICATION_JSON)
                .body(new EnvioDocumento(telefone, link, nomeArquivo, legenda))
                .retrieve().body(RespostaEnvio.class);
    }

    RespostaEnvio enviarAudio(String telefone, String link) {
        return post("/send-audio", new EnvioAudio(telefone, link));
    }

    RespostaEnvio enviarVideo(String telefone, String link, String legenda) {
        return post("/send-video", new EnvioVideo(telefone, link, legenda));
    }

    void lerMensagem(String telefone, String messageId) {
        http.post().uri("/read-message").contentType(MediaType.APPLICATION_JSON)
                .body(new LeituraMensagem(telefone, messageId))
                .retrieve().toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> codigoPorTelefone(String telefone) {
        return http.get().uri("/phone-code/{telefone}", telefone).retrieve().body(Map.class);
    }

    private RespostaEnvio post(String rota, Object corpo) {
        return http.post().uri(rota).contentType(MediaType.APPLICATION_JSON).body(corpo)
                .retrieve().body(RespostaEnvio.class);
    }

    RespostaStatus status() {
        return http.get().uri("/status").retrieve().body(RespostaStatus.class);
    }

    @SuppressWarnings("unchecked")
    Map<String, Object> qrCodeImagem() {
        return http.get().uri("/qr-code/image").retrieve().body(Map.class);
    }

    List<GrupoZApi> grupos(int pagina, int tamanhoPagina) {
        GrupoZApi[] grupos = http.get().uri("/groups?page={pagina}&pageSize={tamanho}", pagina, tamanhoPagina)
                .retrieve().body(GrupoZApi[].class);
        return grupos == null ? List.of() : List.of(grupos);
    }

    List<ContatoZApi> contatos(int pagina, int tamanhoPagina) {
        ContatoZApi[] contatos = http.get().uri("/contacts?page={pagina}&pageSize={tamanho}", pagina, tamanhoPagina)
                .retrieve().body(ContatoZApi[].class);
        return contatos == null ? List.of() : List.of(contatos);
    }

    void desconectar() {
        http.get().uri("/disconnect").retrieve().toBodilessEntity();
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
