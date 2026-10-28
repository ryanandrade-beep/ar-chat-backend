package br.com.archat.aplicacao.autenticacao;

import java.time.Instant;
import java.util.UUID;

/**
 * Quem está fazendo a requisição: o dono de uma workspace (CLIENTE, {@code id} do cliente), um operador da
 * workspace (OPERADOR, {@code id} do operador) ou um administrador da plataforma.
 */
public record Sessao(Perfil perfil, UUID id, Instant expiraEm) {

    public enum Perfil {
        CLIENTE,
        OPERADOR,
        ADMINISTRADOR
    }
}
