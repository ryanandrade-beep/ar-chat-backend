package br.com.archat.dominio.canal;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Instância da Z-API de uma empresa: o número de WhatsApp dela. O backoffice vincula o ID e o token; quem lê
 * o QR Code (ou digita o código) é o administrador da empresa.
 */
public record InstanciaWhatsApp(UUID clienteId, String instancia, String token, Instant vinculadaEm) {

    public InstanciaWhatsApp {
        Objects.requireNonNull(clienteId);
        Objects.requireNonNull(vinculadaEm);
        instancia = instancia == null ? "" : instancia.strip();
        token = token == null ? "" : token.strip();
        if (!instancia.matches("[A-Za-z0-9]{8,64}") || !token.matches("[A-Za-z0-9]{8,128}")) {
            throw new RegraDeNegocioException("Informe o ID e o token da instância da Z-API (somente letras e números)");
        }
    }

    public String tokenMascarado() {
        return token.substring(0, 4) + "…" + token.substring(token.length() - 4);
    }
}
