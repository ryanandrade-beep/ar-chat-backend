package br.com.archat.aplicacao.atendimento;

import br.com.archat.aplicacao.porta.AtendimentoRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.FiltroMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.mensagem.Mensagem;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Histórico de um atendimento (ticket): as mensagens da conversa trocadas entre a abertura e a finalização,
 * incluindo as notas internas, em ordem cronológica. Uma conversa tem vários atendimentos ao longo do tempo;
 * aqui entra só o trecho do atendimento pedido.
 */
public class ConsultarHistoricoDoAtendimentoUseCase {

    private final AtendimentoRepositorio atendimentos;
    private final ConversaRepositorio conversas;
    private final DestinatarioRepositorio destinatarios;
    private final MensagemRepositorio mensagens;

    public ConsultarHistoricoDoAtendimentoUseCase(AtendimentoRepositorio atendimentos, ConversaRepositorio conversas,
                                                  DestinatarioRepositorio destinatarios, MensagemRepositorio mensagens) {
        this.atendimentos = atendimentos;
        this.conversas = conversas;
        this.destinatarios = destinatarios;
        this.mensagens = mensagens;
    }

    public Historico buscar(UUID clienteId, UUID atendimentoId) {
        Atendimento atendimento = atendimentos.buscarPorId(atendimentoId)
                .filter(a -> a.clienteId().equals(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Atendimento"));
        Conversa conversa = conversas.buscarPorId(atendimento.conversaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa"));
        Destinatario destinatario = destinatarios.buscarPorId(conversa.destinatarioId()).orElse(null);
        Instant inicio = atendimento.abertoEm().minusSeconds(1);
        Instant fim = atendimento.finalizadoEm();
        List<Mensagem> daConversa = mensagens.listar(new FiltroMensagens(clienteId, conversa.id(), null, null)).stream()
                .filter(m -> !m.criadaEm().isBefore(inicio) && (fim == null || !m.criadaEm().isAfter(fim.plusSeconds(1))))
                .sorted(Comparator.comparing(Mensagem::criadaEm))
                .toList();
        return new Historico(atendimento, conversa, destinatario, daConversa);
    }

    public record Historico(Atendimento atendimento, Conversa conversa, Destinatario destinatario, List<Mensagem> mensagens) {
    }
}
