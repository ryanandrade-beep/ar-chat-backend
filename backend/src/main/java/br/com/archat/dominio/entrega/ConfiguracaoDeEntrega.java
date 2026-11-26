package br.com.archat.dominio.entrega;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.util.UUID;

/** Avisos automáticos de entrega enviados ao cliente no WhatsApp. {nome} vira o nome do cliente. */
public record ConfiguracaoDeEntrega(UUID clienteId, boolean avisarSaida, String mensagemSaida, boolean avisarEntregue,
                                    String mensagemEntregue, String pixChave, String pixTipo, String pixNome,
                                    boolean aceitaCartao) {

    public static final java.util.Set<String> TIPOS_DE_CHAVE = java.util.Set.of("cpf", "cnpj", "phone", "email", "randomKey");

    public static final String SAIDA_PADRAO = "Olá {nome}! Seu pedido já saiu para entrega e está a caminho. Em breve chega aí!";
    public static final String ENTREGUE_PADRAO = "Olá {nome}! Seu pedido foi entregue. Obrigado pela preferência!";

    public ConfiguracaoDeEntrega {
        mensagemSaida = texto(mensagemSaida);
        mensagemEntregue = texto(mensagemEntregue);
        pixChave = pixChave == null || pixChave.isBlank() ? null : pixChave.strip();
        pixNome = pixNome == null || pixNome.isBlank() ? null : pixNome.strip();
        pixTipo = pixTipo == null || pixTipo.isBlank() ? null : pixTipo.strip();
        if (pixChave != null && (pixChave.length() > 140 || pixTipo == null || !TIPOS_DE_CHAVE.contains(pixTipo))) {
            throw new RegraDeNegocioException("Informe o tipo da chave Pix: cpf, cnpj, phone, email ou randomKey");
        }
        if (pixNome != null && pixNome.length() > 80) {
            throw new RegraDeNegocioException("O nome da chave Pix deve ter no máximo 80 caracteres");
        }
    }

    public boolean temPix() {
        return pixChave != null && pixTipo != null;
    }

    public static ConfiguracaoDeEntrega padrao(UUID clienteId) {
        return new ConfiguracaoDeEntrega(clienteId, true, SAIDA_PADRAO, true, ENTREGUE_PADRAO, null, null, null, true);
    }

    private static String texto(String valor) {
        String limpo = valor == null ? "" : valor.strip();
        if (limpo.isEmpty() || limpo.length() > 500) {
            throw new RegraDeNegocioException("A mensagem do aviso deve ter de 1 a 500 caracteres");
        }
        return limpo;
    }

    public String saidaPara(String nome) {
        return mensagemSaida.replace("{nome}", nome);
    }

    public String entreguePara(String nome) {
        return mensagemEntregue.replace("{nome}", nome);
    }
}
