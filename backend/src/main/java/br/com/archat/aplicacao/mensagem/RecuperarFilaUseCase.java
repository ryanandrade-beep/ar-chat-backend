package br.com.archat.aplicacao.mensagem;

import br.com.archat.aplicacao.porta.FilaMensagens;
import br.com.archat.aplicacao.porta.LiberacoesDeMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.mensagem.Mensagem;

import java.time.Clock;
import java.util.List;

/**
 * A fila vive em memória; ao iniciar o sistema, ela é reconstruída a partir das mensagens
 * que ficaram pendentes no banco, na ordem de chegada. As que aguardam o horário de uma campanha agendada
 * continuam retidas até o horário.
 */
public class RecuperarFilaUseCase {

    private final MensagemRepositorio mensagens;
    private final FilaMensagens fila;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;
    private final LiberacoesDeMensagens liberacoes;

    public RecuperarFilaUseCase(MensagemRepositorio mensagens, FilaMensagens fila,
                                UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this(mensagens, fila, unidadeDeTrabalho, relogio, null);
    }

    public RecuperarFilaUseCase(MensagemRepositorio mensagens, FilaMensagens fila,
                                UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio, LiberacoesDeMensagens liberacoes) {
        this.liberacoes = liberacoes;
        this.mensagens = mensagens;
        this.fila = fila;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public int executar() {
        List<Mensagem> pendentes = unidadeDeTrabalho.executar(() -> {
            List<Mensagem> lista = mensagens.listarPendentes().stream()
                    .filter(m -> liberacoes == null || !liberacoes.existe(m.id())).toList();
            lista.forEach(m -> {
                m.devolverParaFila(relogio.instant());
                mensagens.salvar(m);
            });
            return lista;
        });
        pendentes.forEach(m -> fila.enfileirar(m.id(), m.prioridade()));
        return pendentes.size();
    }
}
