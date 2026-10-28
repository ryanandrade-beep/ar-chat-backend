package br.com.archat.aplicacao.whatsapp;

import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp.SituacaoConexao;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp.GrupoWhatsApp;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.util.List;
import java.util.UUID;

/**
 * Conecta (via QR Code ou código pelo telefone), consulta e desconecta o número de WhatsApp da plataforma.
 * Desconectar ou conectar outro número não apaga nada: conversas, mensagens e atendimentos ficam no banco do
 * sistema e continuam visíveis; as novas mensagens seguem pelo número conectado.
 */
public class GerenciarConexaoWhatsAppUseCase {

    private final ConexaoWhatsApp conexao;

    public GerenciarConexaoWhatsAppUseCase(ConexaoWhatsApp conexao) {
        this.conexao = conexao;
    }

    public SituacaoConexao situacao(UUID clienteId) {
        return conexao.situacao(clienteId);
    }

    public String qrCode(UUID clienteId) {
        if (conexao.situacao(clienteId).conectado()) {
            throw new RegraDeNegocioException("O WhatsApp já está conectado");
        }
        return conexao.qrCode(clienteId);
    }

    public String codigoPorTelefone(UUID clienteId, String telefone) {
        String numero = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (numero.length() < 10 || numero.length() > 13) {
            throw new RegraDeNegocioException("Informe o número do WhatsApp a conectar, com DDD (ex.: 5544999999999)");
        }
        if (conexao.situacao(clienteId).conectado()) {
            throw new RegraDeNegocioException("O WhatsApp já está conectado");
        }
        return conexao.codigoPorTelefone(clienteId, numero);
    }

    public void desconectar(UUID clienteId) {
        conexao.desconectar(clienteId);
    }

    public List<GrupoWhatsApp> listarGrupos(UUID clienteId, int pagina, int tamanhoPagina) {
        if (pagina < 1 || tamanhoPagina < 1 || tamanhoPagina > 100) {
            throw new RegraDeNegocioException("Use página a partir de 1 e tamanho de 1 a 100");
        }
        return conexao.listarGrupos(clienteId, pagina, tamanhoPagina);
    }
}
