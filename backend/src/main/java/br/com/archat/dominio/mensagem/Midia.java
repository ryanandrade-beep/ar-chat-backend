package br.com.archat.dominio.mensagem;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Arquivo enviado ou recebido numa mensagem: um arquivo guardado pelo sistema ({@code /media/{id}}, enviado ao
 * canal em base64) ou um link público (https), como os das mídias recebidas. {@code nomeArquivo} é usado nos documentos.
 */
public record Midia(TipoMidia tipo, String url, String nomeArquivo) {

    public static final int TAMANHO_MAXIMO_URL = 2048;
    private static final Pattern EXTENSAO = Pattern.compile("[a-z0-9]{1,10}");

    public Midia {
        if (tipo == null) {
            throw new RegraDeNegocioException("O tipo da mídia é obrigatório");
        }
        if (url == null || url.isBlank()) {
            throw new RegraDeNegocioException("O link da mídia é obrigatório");
        }
        url = url.strip();
        boolean arquivoInterno = url.matches("/media/[0-9a-fA-F-]{36}");
        if (url.length() > TAMANHO_MAXIMO_URL || !(arquivoInterno || url.startsWith("https://") || url.startsWith("http://"))) {
            throw new RegraDeNegocioException("Envie um arquivo ou informe o link público (http ou https) da mídia");
        }
        nomeArquivo = nomeArquivo == null || nomeArquivo.isBlank() ? null : nomeArquivo.strip();
        if (nomeArquivo != null && nomeArquivo.length() > 255) {
            throw new RegraDeNegocioException("O nome do arquivo deve ter no máximo 255 caracteres");
        }
        if (tipo == TipoMidia.DOCUMENTO && extensaoDe(nomeArquivo, url) == null) {
            throw new RegraDeNegocioException("Não foi possível identificar a extensão do documento (ex.: pdf, docx); "
                    + "informe o nome do arquivo com a extensão");
        }
    }

    public String extensao() {
        return extensaoDe(nomeArquivo, url);
    }

    private static String extensaoDe(String nomeArquivo, String url) {
        String extensao = extensaoDoNome(nomeArquivo);
        if (extensao == null) {
            try {
                extensao = extensaoDoNome(URI.create(url).getPath());
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return extensao;
    }

    private static String extensaoDoNome(String nome) {
        if (nome == null) {
            return null;
        }
        int ponto = nome.lastIndexOf('.');
        if (ponto < 0 || ponto == nome.length() - 1 || nome.indexOf('/', ponto) >= 0) {
            return null;
        }
        String extensao = nome.substring(ponto + 1).toLowerCase(Locale.ROOT);
        return EXTENSAO.matcher(extensao).matches() ? extensao : null;
    }
}
