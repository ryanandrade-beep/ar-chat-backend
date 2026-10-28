package br.com.archat.aplicacao.cliente;

import br.com.archat.aplicacao.porta.TransacaoRepositorio;
import br.com.archat.dominio.financeiro.Transacao;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Extrato do cliente (créditos, débitos, estornos e ajustes) dos últimos 90 dias, do mais recente ao mais antigo. */
public class ConsultarExtratoUseCase {

    private static final Duration PERIODO = Duration.ofDays(90);

    private final TransacaoRepositorio transacoes;
    private final Clock relogio;

    public ConsultarExtratoUseCase(TransacaoRepositorio transacoes, Clock relogio) {
        this.transacoes = transacoes;
        this.relogio = relogio;
    }

    public List<Transacao> ultimas(UUID clienteId, int limite) {
        Instant agora = relogio.instant();
        return transacoes.listar(clienteId, agora.minus(PERIODO), agora.plusSeconds(1)).stream()
                .sorted(Comparator.comparing(Transacao::dataHora).reversed())
                .limit(Math.max(1, Math.min(limite, 100)))
                .toList();
    }
}
