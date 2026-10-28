package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.mensagem.ContatoCompartilhado;
import br.com.archat.dominio.mensagem.Midia;
import br.com.archat.dominio.mensagem.Prioridade;

import java.util.UUID;

/**
 * Canal externo que entrega a mensagem ao destinatário (simulado ou WhatsApp via Z-API).
 * Só é chamado depois que a mensagem já foi validada, cobrada e retirada da fila.
 */
public interface EnvioMensagemGateway {

    ResultadoEnvio enviar(Envio envio);

    record Envio(UUID mensagemId, UUID clienteId, String telefone, String conteudo, Midia midia, Prioridade prioridade,
                 int tentativa, ContatoCompartilhado contato) {

        public Envio(UUID mensagemId, UUID clienteId, String telefone, String conteudo, Midia midia,
                     Prioridade prioridade, int tentativa) {
            this(mensagemId, clienteId, telefone, conteudo, midia, prioridade, tentativa, null);
        }
    }

    record ResultadoEnvio(boolean aceito, String idExterno) {

        public static ResultadoEnvio aceito(String idExterno) {
            return new ResultadoEnvio(true, idExterno);
        }

        public static ResultadoEnvio recusado() {
            return new ResultadoEnvio(false, null);
        }
    }
}
