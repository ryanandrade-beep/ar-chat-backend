package br.com.archat.aplicacao.porta;

/** Faz a chamada HTTP ao sistema da empresa. Nunca lança exceção: devolve o resultado, com sucesso ou não. */
public interface EnvioDeWebhook {

    Resposta enviar(String url, String segredo, String idEnvio, String evento, String corpoJson);

    record Resposta(boolean sucesso, Integer httpStatus, String resumo) {
    }
}
