package br.com.archat.dominio.integracao;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Para onde e quais eventos a empresa quer receber. O segredo assina cada envio (HMAC-SHA256). */
public record ConfiguracaoDeIntegracao(UUID clienteId, String url, String segredo, Set<EventoDeIntegracao> eventos,
                                       boolean ativa, Instant atualizadaEm) {
    public ConfiguracaoDeIntegracao {
        Objects.requireNonNull(clienteId);
        Objects.requireNonNull(atualizadaEm);
        url = validarUrl(url);
        if (segredo == null || segredo.isBlank()) {
            throw new RegraDeNegocioException("O segredo da integração é obrigatório");
        }
        if (eventos == null || eventos.isEmpty()) {
            throw new RegraDeNegocioException("Marque ao menos um evento para receber");
        }
        if (eventos.stream().anyMatch(e -> !e.assinavel())) {
            throw new RegraDeNegocioException("Evento inválido");
        }
        eventos = Set.copyOf(eventos);
    }

    public boolean recebe(EventoDeIntegracao evento) {
        return ativa && eventos.contains(evento);
    }

    public ConfiguracaoDeIntegracao com(String novaUrl, Set<EventoDeIntegracao> novosEventos, boolean novaAtiva, Instant agora) {
        return new ConfiguracaoDeIntegracao(clienteId, novaUrl, segredo, novosEventos, novaAtiva, agora);
    }

    public ConfiguracaoDeIntegracao comSegredo(String novoSegredo, Instant agora) {
        return new ConfiguracaoDeIntegracao(clienteId, url, novoSegredo, eventos, ativa, agora);
    }

    public static Set<EventoDeIntegracao> eventosPadrao() {
        return EnumSet.of(EventoDeIntegracao.ATENDIMENTO_FINALIZADO);
    }

    public static String validarUrl(String informada) {
        String texto = informada == null ? "" : informada.strip();
        if (texto.isEmpty() || texto.length() > 500) {
            throw new RegraDeNegocioException("Informe a URL que vai receber os avisos (até 500 caracteres)");
        }
        URI uri;
        try {
            uri = new URI(texto);
        } catch (URISyntaxException e) {
            throw new RegraDeNegocioException("A URL da integração é inválida");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new RegraDeNegocioException("A URL deve começar com https://");
        }
        if (uri.getUserInfo() != null) {
            throw new RegraDeNegocioException("A URL não pode conter usuário e senha");
        }
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")
                || !host.contains(".") && !host.contains(":")) {
            throw new RegraDeNegocioException("A URL deve apontar para um endereço público");
        }
        if (pareceEnderecoIp(host) && enderecoInterno(host)) {
            throw new RegraDeNegocioException("A URL deve apontar para um endereço público");
        }
        return texto;
    }

    private static boolean pareceEnderecoIp(String host) {
        return host.matches("[0-9.]+") || host.contains(":");
    }

    private static boolean enderecoInterno(String literal) {
        try {
            return enderecoInterno(InetAddress.getByName(literal.replace("[", "").replace("]", "")));
        } catch (java.net.UnknownHostException e) {
            return true;
        }
    }

    public static boolean enderecoInterno(InetAddress a) {
        byte[] b = a.getAddress();
        boolean ulaIpv6 = b.length == 16 && (b[0] & 0xFE) == 0xFC;
        boolean cgnat = b.length == 4 && (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
        return a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()
                || a.isMulticastAddress() || ulaIpv6 || cgnat;
    }
}
