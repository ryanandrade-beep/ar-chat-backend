package br.com.archat.infraestrutura.envio.zapi;

import br.com.archat.aplicacao.excecao.CanalIndisponivelException;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(name = "archat.envio.provedor", havingValue = "zapi")
class ConexaoWhatsAppZApi implements ConexaoWhatsApp {

    private static final Pattern ERRO_ZAPI = Pattern.compile("\"(?:error|message)\"\\s*:\\s*\"([^\"]+)\"");

    private final ClienteZApi zapi;
    private final InstanciasZApi instancias;

    ConexaoWhatsAppZApi(ClienteZApi zapi, InstanciasZApi instancias) {
        this.zapi = zapi;
        this.instancias = instancias;
    }

    @Override
    public SituacaoConexao situacao(UUID clienteId) {
        ClienteZApi.RespostaStatus status = chamar(() -> zapi.status(instancias.da(clienteId)));
        return new SituacaoConexao("zapi", Boolean.TRUE.equals(status.connected()),
                Boolean.TRUE.equals(status.smartphoneConnected()), status.error());
    }

    @Override
    public String qrCode(UUID clienteId) {
        Map<String, Object> resposta = chamar(() -> zapi.qrCodeImagem(instancias.da(clienteId)));
        Object imagem = resposta == null ? null : resposta.get("value");
        if (imagem == null) {
            throw new RegraDeNegocioException("A Z-API não devolveu o QR Code (o aparelho pode exigir passkey); "
                    + "conecte pelo painel da Z-API");
        }
        String base64 = imagem.toString();
        return base64.startsWith("data:") ? base64 : "data:image/png;base64," + base64;
    }

    @Override
    public String codigoPorTelefone(UUID clienteId, String telefone) {
        Map<String, Object> resposta = chamar(() -> zapi.codigoPorTelefone(instancias.da(clienteId),
                EnvioMensagemZApi.comDdi(telefone)));
        Object codigo = resposta == null ? null : resposta.get("value");
        if (codigo == null) {
            throw new RegraDeNegocioException("A Z-API não devolveu o código (o aparelho pode exigir passkey); "
                    + "conecte pelo QR Code ou pelo painel da Z-API");
        }
        return codigo.toString();
    }

    @Override
    public void marcarComoLida(UUID clienteId, String telefoneOuGrupo, String idExterno) {
        chamar(() -> {
            zapi.lerMensagem(instancias.da(clienteId), EnvioMensagemZApi.comDdi(telefoneOuGrupo), idExterno);
            return null;
        });
    }

    @Override
    public void enviarAvisoDoSistema(UUID clienteId, String telefone, String texto) {
        chamar(() -> zapi.enviarTexto(instancias.da(clienteId), EnvioMensagemZApi.comDdi(telefone), texto));
    }

    @Override
    public void desconectar(UUID clienteId) {
        chamar(() -> {
            zapi.desconectar(instancias.da(clienteId));
            return null;
        });
    }

    @Override
    public List<ContatoWhatsApp> listarContatos(UUID clienteId, int pagina, int tamanhoPagina) {
        return chamar(() -> zapi.contatos(instancias.da(clienteId), pagina, tamanhoPagina)).stream()
                .filter(c -> c.phone() != null && c.phone().matches("\\d{10,15}"))
                .map(c -> new ContatoWhatsApp(c.phone(), primeiroPreenchido(c.name(), c.curto(), c.vname(), c.apelido(), c.phone())))
                .toList();
    }

    private static String primeiroPreenchido(String... valores) {
        for (String valor : valores) {
            if (valor != null && !valor.isBlank()) {
                return valor.strip();
            }
        }
        return "";
    }

    @Override
    public List<GrupoWhatsApp> listarGrupos(UUID clienteId, int pagina, int tamanhoPagina) {
        return chamar(() -> zapi.grupos(instancias.da(clienteId), pagina, tamanhoPagina)).stream()
                .filter(g -> !Boolean.FALSE.equals(g.isGroup()))
                .map(g -> new GrupoWhatsApp(g.phone(), g.name()))
                .toList();
    }

    private static <T> T chamar(Supplier<T> operacao) {
        try {
            return operacao.get();
        } catch (RestClientResponseException e) {
            Matcher erro = ERRO_ZAPI.matcher(e.getResponseBodyAsString());
            throw new CanalIndisponivelException("A Z-API recusou a operação (HTTP " + e.getStatusCode().value() + "): "
                    + (erro.find() ? erro.group(1) : e.getStatusText()));
        } catch (RestClientException e) {
            throw new CanalIndisponivelException("A Z-API não respondeu: " + e.getMessage());
        }
    }
}
