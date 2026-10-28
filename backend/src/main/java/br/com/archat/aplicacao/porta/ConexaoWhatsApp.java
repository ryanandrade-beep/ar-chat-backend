package br.com.archat.aplicacao.porta;

import java.util.List;
import java.util.UUID;

/**
 * Conexão do WhatsApp de cada empresa. {@code clienteId} identifica a empresa (a instância dela na Z-API);
 * nulo usa a instância da própria plataforma (ex.: código de recuperação de senha).
 */
public interface ConexaoWhatsApp {

    SituacaoConexao situacao(UUID clienteId);

    String qrCode(UUID clienteId);

    String codigoPorTelefone(UUID clienteId, String telefone);

    void marcarComoLida(UUID clienteId, String telefoneOuGrupo, String idExterno);

    void enviarAvisoDoSistema(UUID clienteId, String telefone, String texto);

    void desconectar(UUID clienteId);

    List<GrupoWhatsApp> listarGrupos(UUID clienteId, int pagina, int tamanhoPagina);

    List<ContatoWhatsApp> listarContatos(UUID clienteId, int pagina, int tamanhoPagina);

    record GrupoWhatsApp(String id, String nome) {
    }

    record ContatoWhatsApp(String telefone, String nome) {
    }

    record SituacaoConexao(String provedor, boolean conectado, boolean celularOnline, String detalhe) {
    }
}
