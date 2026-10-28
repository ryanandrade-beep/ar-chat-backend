package br.com.archat.dominio.mensagem;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

/** Cartão de contato enviado ou recebido no WhatsApp (rota send-contact da Z-API). */
public record ContatoCompartilhado(String nome, String telefone) {

    public ContatoCompartilhado {
        nome = nome == null ? "" : nome.strip();
        telefone = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (nome.isEmpty() || nome.length() > 120) {
            throw new RegraDeNegocioException("Informe o nome do contato (até 120 caracteres)");
        }
        if (telefone.length() < 10 || telefone.length() > 15) {
            throw new RegraDeNegocioException("Informe o telefone do contato com DDD");
        }
    }
}
