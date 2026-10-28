package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.cliente.ContaDeAcesso;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.util.Objects;
import java.util.UUID;

/**
 * Usuário da área administrativa. A senha só existe aqui na forma de hash. E-mail e telefone são opcionais
 * (o administrador inicial nasce sem eles) e servem de contato para recuperar o acesso.
 */
public record Administrador(UUID id, String nome, String login, String senhaHash, String email, String telefone) {

    public Administrador(UUID id, String nome, String login, String senhaHash) {
        this(id, nome, login, senhaHash, null, null);
    }

    public Administrador {
        Objects.requireNonNull(id);
        Objects.requireNonNull(senhaHash);
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Nome do administrador é obrigatório");
        }
        if (login == null || !login.strip().matches("[a-zA-Z0-9._-]{3,50}")) {
            throw new RegraDeNegocioException("Login deve ter de 3 a 50 caracteres (letras, números, . _ -)");
        }
        nome = nome.strip();
        login = login.strip().toLowerCase();
        email = email == null || email.isBlank() ? null : ContaDeAcesso.normalizarEmail(email);
        if (telefone != null && !telefone.isBlank()) {
            telefone = telefone.replaceAll("\\D", "");
            if (telefone.length() < 10 || telefone.length() > 13) {
                throw new RegraDeNegocioException("Informe o telefone com DDD");
            }
        } else {
            telefone = null;
        }
    }
}
