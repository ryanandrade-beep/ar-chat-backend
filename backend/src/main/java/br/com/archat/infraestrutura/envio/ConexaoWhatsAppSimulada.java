package br.com.archat.infraestrutura.envio;

import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@ConditionalOnProperty(name = "archat.envio.provedor", havingValue = "simulado", matchIfMissing = true)
class ConexaoWhatsAppSimulada implements ConexaoWhatsApp {

    @Override
    public SituacaoConexao situacao(java.util.UUID clienteId) {
        return new SituacaoConexao("simulado", true, true, "Envio simulado: nenhuma conexão real é necessária");
    }

    @Override
    public String qrCode(java.util.UUID clienteId) {
        throw new RegraDeNegocioException("O envio está em modo simulado; configure archat.envio.provedor=zapi");
    }

    @Override
    public String codigoPorTelefone(java.util.UUID clienteId, String telefone) {
        throw new RegraDeNegocioException("O envio está em modo simulado; configure archat.envio.provedor=zapi");
    }

    @Override
    public void marcarComoLida(java.util.UUID clienteId, String telefoneOuGrupo, String idExterno) {
    }

    @Override
    public List<ContatoWhatsApp> listarContatos(java.util.UUID clienteId, int pagina, int tamanhoPagina) {
        return List.of();
    }

    @Override
    public void enviarAvisoDoSistema(java.util.UUID clienteId, String telefone, String texto) {
        LoggerFactory.getLogger(ConexaoWhatsAppSimulada.class).info("WhatsApp simulado para {}: {}", telefone, texto);
    }

    @Override
    public List<GrupoWhatsApp> listarGrupos(java.util.UUID clienteId, int pagina, int tamanhoPagina) {
        return List.of();
    }

    @Override
    public void desconectar(java.util.UUID clienteId) {
        throw new RegraDeNegocioException("O envio está em modo simulado; não há conexão para desfazer");
    }
}
