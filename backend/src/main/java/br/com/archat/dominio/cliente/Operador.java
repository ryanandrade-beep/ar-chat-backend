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

    private final UUID id;
    private final UUID clienteId;
    private String nome;
    private final String email;
    private String senhaHash;
    private boolean ativo;
    private final Instant criadoEm;

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

    private static String nomeValido(String nome) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 120) {
            throw new RegraDeNegocioException("Informe o nome do operador (até 120 caracteres)");
        }
        return nome.strip();
    }

    public void alterar(String novoNome, boolean ativo) {
        this.nome = nomeValido(novoNome);
        this.ativo = ativo;
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
}
