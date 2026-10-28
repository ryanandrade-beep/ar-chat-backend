package br.com.archat.dominio.departamento;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Setor da workspace (ex.: Comercial, Financeiro, Entregas) para onde a URA encaminha o cliente final. */
public class Departamento {

    private final UUID id;
    private final UUID clienteId;
    private String nome;
    private boolean ativo;
    private final Instant criadoEm;

    private Departamento(UUID id, UUID clienteId, String nome, boolean ativo, Instant criadoEm) {
        this.id = Objects.requireNonNull(id);
        this.clienteId = Objects.requireNonNull(clienteId);
        this.nome = nomeValido(nome);
        this.ativo = ativo;
        this.criadoEm = Objects.requireNonNull(criadoEm);
    }

    public static Departamento novo(UUID clienteId, String nome, Instant agora) {
        return new Departamento(UUID.randomUUID(), clienteId, nome, true, agora);
    }

    public static Departamento reconstituir(UUID id, UUID clienteId, String nome, boolean ativo, Instant criadoEm) {
        return new Departamento(id, clienteId, nome, ativo, criadoEm);
    }

    private static String nomeValido(String nome) {
        if (nome == null || nome.isBlank() || nome.strip().length() > 60) {
            throw new RegraDeNegocioException("Informe o nome do departamento (até 60 caracteres)");
        }
        return nome.strip();
    }

    public void alterar(String nome, boolean ativo) {
        this.nome = nomeValido(nome);
        this.ativo = ativo;
    }

    public boolean pertenceA(UUID clienteId) {
        return this.clienteId.equals(clienteId);
    }

    public UUID id() { return id; }
    public UUID clienteId() { return clienteId; }
    public String nome() { return nome; }
    public boolean ativo() { return ativo; }
    public Instant criadoEm() { return criadoEm; }
}
