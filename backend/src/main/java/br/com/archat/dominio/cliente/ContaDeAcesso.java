package br.com.archat.dominio.cliente;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Dados de acesso do cliente ao painel: e-mail, telefone de contato, senha (só o hash) e o aceite dos termos.
 * Também guarda o código temporário de recuperação de senha (só o hash), com validade e limite de tentativas.
 */
public class ContaDeAcesso {

    public static final int TAMANHO_MINIMO_SENHA = 8;
    public static final Duration VALIDADE_CODIGO = Duration.ofMinutes(15);
    public static final int MAXIMO_TENTATIVAS_CODIGO = 5;
    private static final Pattern EMAIL = Pattern.compile(
            "^[a-z0-9]+(?:[._%+-][a-z0-9]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z]{2,}$");

    private final UUID clienteId;
    private final String email;
    private final String telefone;
    private String senhaHash;
    private final String versaoTermos;
    private final Instant termosAceitosEm;
    private String codigoHash;
    private Instant codigoExpiraEm;
    private int tentativasCodigo;

    private ContaDeAcesso(UUID clienteId, String email, String telefone, String senhaHash, String versaoTermos,
                          Instant termosAceitosEm, String codigoHash, Instant codigoExpiraEm, int tentativasCodigo) {
        this.clienteId = Objects.requireNonNull(clienteId);
        this.email = Objects.requireNonNull(email);
        this.telefone = telefone;
        this.senhaHash = Objects.requireNonNull(senhaHash);
        this.versaoTermos = versaoTermos;
        this.termosAceitosEm = termosAceitosEm;
        this.codigoHash = codigoHash;
        this.codigoExpiraEm = codigoExpiraEm;
        this.tentativasCodigo = tentativasCodigo;
    }

    public static ContaDeAcesso nova(UUID clienteId, String email, String telefone, String senhaHash,
                                     boolean aceitouTermos, String versaoTermos, Instant agora) {
        if (!aceitouTermos) {
            throw new RegraDeNegocioException("É preciso aceitar os Termos de Uso e a Política de Privacidade");
        }
        return new ContaDeAcesso(clienteId, normalizarEmail(email), telefoneValido(telefone), senhaHash, versaoTermos,
                agora, null, null, 0);
    }

    public static ContaDeAcesso reconstituir(UUID clienteId, String email, String telefone, String senhaHash,
                                             String versaoTermos, Instant termosAceitosEm, String codigoHash,
                                             Instant codigoExpiraEm, int tentativasCodigo) {
        return new ContaDeAcesso(clienteId, email, telefone, senhaHash, versaoTermos, termosAceitosEm, codigoHash,
                codigoExpiraEm, tentativasCodigo);
    }

    public static String normalizarEmail(String email) {
        String valor = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        if (valor.length() > 160 || !EMAIL.matcher(valor).matches()) {
            throw new RegraDeNegocioException("Informe um e-mail válido");
        }
        return valor;
    }

    public static void validarSenha(String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA || senha.length() > 72
                || !senha.matches(".*[A-Za-z].*") || !senha.matches(".*\\d.*")) {
            throw new RegraDeNegocioException("A senha deve ter de 8 a 72 caracteres, com letras e números");
        }
    }

    private static String telefoneValido(String telefone) {
        String numeros = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (numeros.length() < 10 || numeros.length() > 13) {
            throw new RegraDeNegocioException("Informe o telefone com DDD");
        }
        return numeros;
    }

    public void registrarCodigoDeRecuperacao(String hashDoCodigo, Instant agora) {
        codigoHash = Objects.requireNonNull(hashDoCodigo);
        codigoExpiraEm = agora.plus(VALIDADE_CODIGO);
        tentativasCodigo = 0;
    }

    public void redefinirSenha(boolean codigoConfere, String novaSenhaHash, Instant agora) {
        if (codigoHash == null || codigoExpiraEm == null || agora.isAfter(codigoExpiraEm)
                || tentativasCodigo >= MAXIMO_TENTATIVAS_CODIGO) {
            throw new RegraDeNegocioException("Código expirado ou inválido; solicite um novo");
        }
        if (!codigoConfere) {
            tentativasCodigo++;
            throw new RegraDeNegocioException("Código incorreto");
        }
        senhaHash = Objects.requireNonNull(novaSenhaHash);
        codigoHash = null;
        codigoExpiraEm = null;
        tentativasCodigo = 0;
    }

    public UUID clienteId() { return clienteId; }
    public String email() { return email; }
    public String telefone() { return telefone; }
    public String senhaHash() { return senhaHash; }
    public String versaoTermos() { return versaoTermos; }
    public Instant termosAceitosEm() { return termosAceitosEm; }
    public String codigoHash() { return codigoHash; }
    public Instant codigoExpiraEm() { return codigoExpiraEm; }
    public int tentativasCodigo() { return tentativasCodigo; }
}
