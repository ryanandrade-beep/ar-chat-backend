package br.com.archat.dominio.arquivo;

import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.TipoMidia;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/** Arquivo enviado pelo painel para uma mensagem. O conteúdo é guardado inteiro (até 16 MB). */
public record Arquivo(UUID id, UUID clienteId, String nome, String tipoMime, byte[] conteudo, Instant criadoEm) {

    public static final int TAMANHO_MAXIMO = 16 * 1024 * 1024;
    public static final String PREFIXO_URL = "/media/";

    public Arquivo {
        Objects.requireNonNull(id);
        Objects.requireNonNull(clienteId);
        Objects.requireNonNull(criadoEm);
        if (conteudo == null || conteudo.length == 0) {
            throw new RegraDeNegocioException("O arquivo está vazio");
        }
        if (conteudo.length > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException("O arquivo deve ter no máximo 16 MB");
        }
        nome = nome == null || nome.isBlank() ? "arquivo" : nome.strip().replaceAll("[\\\\/]", "_");
        if (nome.length() > 255) {
            nome = nome.substring(nome.length() - 255);
        }
        tipoMime = tipoMime == null || tipoMime.isBlank() ? "application/octet-stream" : tipoMime.strip().toLowerCase(Locale.ROOT);
    }

    public static Arquivo novo(UUID clienteId, String nome, String tipoMime, byte[] conteudo, Instant agora) {
        return new Arquivo(UUID.randomUUID(), clienteId, nome, tipoMime, conteudo, agora);
    }

    public TipoMidia tipoDeMidia() {
        if (tipoMime.startsWith("image/")) return TipoMidia.IMAGEM;
        if (tipoMime.startsWith("video/")) return TipoMidia.VIDEO;
        if (tipoMime.startsWith("audio/")) return TipoMidia.AUDIO;
        return TipoMidia.DOCUMENTO;
    }

    public String url() {
        return PREFIXO_URL + id;
    }

    public String comoDataUri() {
        return "data:" + tipoMime + ";base64," + java.util.Base64.getEncoder().encodeToString(conteudo);
    }

    public static java.util.Optional<UUID> idDaUrl(String url) {
        if (url == null || !url.startsWith(PREFIXO_URL)) {
            return java.util.Optional.empty();
        }
        try {
            return java.util.Optional.of(UUID.fromString(url.substring(PREFIXO_URL.length())));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
