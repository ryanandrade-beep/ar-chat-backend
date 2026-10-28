package br.com.archat.aplicacao.porta;

import java.util.List;

/** Conexão do número de WhatsApp usado pelo canal de envio. */
public interface ConexaoWhatsApp {

    SituacaoConexao situacao();

    String qrCode();

    String codigoPorTelefone(String telefone);

    void marcarComoLida(String telefoneOuGrupo, String idExterno);

    void enviarAvisoDoSistema(String telefone, String texto);

    void desconectar();

    List<GrupoWhatsApp> listarGrupos(int pagina, int tamanhoPagina);

    List<ContatoWhatsApp> listarContatos(int pagina, int tamanhoPagina);

    record GrupoWhatsApp(String id, String nome) {
    }

    record ContatoWhatsApp(String telefone, String nome) {
    }

    record SituacaoConexao(String provedor, boolean conectado, boolean celularOnline, String detalhe) {
    }
}
