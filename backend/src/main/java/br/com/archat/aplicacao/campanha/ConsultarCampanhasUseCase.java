package br.com.archat.aplicacao.campanha;

import br.com.archat.aplicacao.porta.CampanhaRepositorio;
import br.com.archat.aplicacao.porta.EnvioDeCampanhas;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.dominio.campanha.Campanha;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.mensagem.StatusMensagem;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Campanhas do cliente com o andamento das mensagens (quantas enfileiradas, entregues, com falha...). */
public class ConsultarCampanhasUseCase {

    private final CampanhaRepositorio campanhas;
    private final MensagemRepositorio mensagens;
    private final EnvioDeCampanhas envios;

    public ConsultarCampanhasUseCase(CampanhaRepositorio campanhas, MensagemRepositorio mensagens) {
        this(campanhas, mensagens, null);
    }

    public ConsultarCampanhasUseCase(CampanhaRepositorio campanhas, MensagemRepositorio mensagens,
                                     EnvioDeCampanhas envios) {
        this.envios = envios;
        this.campanhas = campanhas;
        this.mensagens = mensagens;
    }

    public List<AndamentoCampanha> listar(UUID clienteId) {
        List<Campanha> lista = campanhas.listarPorCliente(clienteId);
        Map<UUID, EnvioDeCampanhas.Envio> programacao = envios == null ? Map.of()
                : envios.buscar(lista.stream().map(Campanha::id).toList());
        return lista.stream().map(c -> comAndamento(c, programacao.get(c.id()))).toList();
    }

    public AndamentoCampanha buscar(UUID clienteId, UUID campanhaId) {
        return campanhas.buscarPorId(campanhaId)
                .filter(c -> c.pertenceA(clienteId))
                .map(c -> comAndamento(c, envios == null ? null : envios.buscar(List.of(c.id())).get(c.id())))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Campanha"));
    }

    private AndamentoCampanha comAndamento(Campanha campanha, EnvioDeCampanhas.Envio envio) {
        return new AndamentoCampanha(campanha, mensagens.contarPorStatusDaCampanha(campanha.id()), envio);
    }

    public record AndamentoCampanha(Campanha campanha, Map<StatusMensagem, Long> mensagensPorStatus,
                                    EnvioDeCampanhas.Envio envio) {

        public AndamentoCampanha(Campanha campanha, Map<StatusMensagem, Long> mensagensPorStatus) {
            this(campanha, mensagensPorStatus, null);
        }
    }
}
