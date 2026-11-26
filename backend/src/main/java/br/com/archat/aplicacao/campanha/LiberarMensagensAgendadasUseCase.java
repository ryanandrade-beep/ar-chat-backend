package br.com.archat.aplicacao.campanha;

import br.com.archat.aplicacao.porta.FilaMensagens;
import br.com.archat.aplicacao.porta.LiberacoesDeMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.StatusMensagem;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * Coloca na fila de envio as mensagens de campanhas agendadas ou com pausa entre envios cujo horário chegou.
 * O horário fica no banco, então o agendamento sobrevive a uma reinicialização do sistema.
 */
public class LiberarMensagensAgendadasUseCase {

    private static final int LOTE = 200;

    private final LiberacoesDeMensagens liberacoes;
    private final MensagemRepositorio mensagens;
    private final FilaMensagens fila;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public LiberarMensagensAgendadasUseCase(LiberacoesDeMensagens liberacoes, MensagemRepositorio mensagens,
                                            FilaMensagens fila, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.liberacoes = liberacoes;
        this.mensagens = mensagens;
        this.fila = fila;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public int executar() {
        List<UUID> vencidas = liberacoes.vencidas(relogio.instant(), LOTE);
        int liberadas = 0;
        for (UUID id : vencidas) {
            Mensagem mensagem = mensagens.buscarPorId(id).orElse(null);
            unidadeDeTrabalho.executar(() -> liberacoes.remover(id));
            if (mensagem != null && mensagem.status() == StatusMensagem.ENFILEIRADA) {
                fila.enfileirar(mensagem.id(), mensagem.prioridade());
                liberadas++;
            }
        }
        return liberadas;
    }
}
