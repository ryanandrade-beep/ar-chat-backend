package br.com.archat.dominio.cliente;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Atendente de uma workspace (cliente). Entra com o próprio e-mail e senha, responde as conversas e pode
 * assumir ou transferir atendimentos. Quem cadastra, desativa e redefine a senha é o dono da workspace.
 */
public class Operador {

    private static final java.util.regex.Pattern NOME =
            java.util.regex.Pattern.compile("^\\p{L}[\\p{L}\\p{M}0-9 '.\\-]*$");

    private final UUID id;
    private final UUID clienteId;
    private String nome;
    private String email;
    private String senhaHash;
    private boolean ativo;
    private final Instant criadoEm;
    private boolean dono;

    private Operador(UUID id, UUID clienteId, String nome, String email, String senhaHash, boolean ativo,
                     Instant criadoEm) {
        this.id = Objects.requireNonNull(id);
        this.clienteId = Objects.requireNonNull(clienteId);
        this.nome = nomeValido(nome);
        this.email = Objects.requireNonNull(email);
        this.senhaHash = Objects.requireNonNull(senhaHash);
        this.ativo = ativo;
        this.criadoEm = Objects.requireNonNull(criadoEm);
    }

    public static Operador novo(UUID clienteId, String nome, String email, String senhaHash, Instant agora) {
        return new Operador(UUID.randomUUID(), clienteId, nome, ContaDeAcesso.normalizarEmail(email), senhaHash, true, agora);
    }

    public static Operador reconstituir(UUID id, UUID clienteId, String nome, String email, String senhaHash,
                                        boolean ativo, Instant criadoEm) {
        return new Operador(id, clienteId, nome, email, senhaHash, ativo, criadoEm);
    }

    public static Operador reconstituir(UUID id, UUID clienteId, String nome, String email, String senhaHash,
                                        boolean ativo, Instant criadoEm, boolean dono) {
        Operador operador = new Operador(id, clienteId, nome, email, senhaHash, ativo, criadoEm);
        operador.dono = dono;
        return operador;
    }

    public static Operador perfilDoDono(UUID clienteId, String nome, Instant agora) {
        Operador operador = new Operador(UUID.randomUUID(), clienteId, nome, "dono-" + clienteId + "@workspace.archat",
                "!", true, agora);
        operador.dono = true;
        return operador;
    }

    public static String nomeValido(String nome) {
        String limpo = nome == null ? "" : nome.strip().replaceAll("\\s+", " ");
        if (limpo.length() < 2 || limpo.length() > 120) {
            throw new RegraDeNegocioException("Informe o nome do operador (de 2 a 120 caracteres)");
        }
        if (!NOME.matcher(limpo).matches()) {
            throw new RegraDeNegocioException("O nome do operador só pode ter letras, números, espaço, apóstrofo, ponto e hífen");
        }
        return limpo;
    }

    public void alterar(String novoNome, boolean ativo) {
        this.nome = nomeValido(novoNome);
        this.ativo = ativo;
    }

    public void trocarEmail(String novoEmail) {
        this.email = Objects.requireNonNull(novoEmail);
    }

    public void trocarSenha(String novaSenhaHash) {
        this.senhaHash = Objects.requireNonNull(novaSenhaHash);
    }

    public boolean pertenceA(UUID clienteId) {
        return this.clienteId.equals(clienteId);
    }

    public UUID id() { return id; }
    public UUID clienteId() { return clienteId; }
    public String nome() { return nome; }
    public String email() { return email; }
    public String senhaHash() { return senhaHash; }
    public boolean ativo() { return ativo; }
    public Instant criadoEm() { return criadoEm; }
    public boolean dono() { return dono; }
}
