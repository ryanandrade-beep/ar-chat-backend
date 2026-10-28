package br.com.archat.dominio.financeiro;

import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.mensagem.Mensagem;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Lançamento no histórico financeiro do cliente. {@code saldoApos} guarda o valor disponível
 * (saldo no pré-pago ou limite restante no pós-pago) logo após o lançamento.
 */
public record Transacao(UUID id, UUID clienteId, UUID mensagemId, TipoTransacao tipo, BigDecimal valor,
                        BigDecimal saldoApos, Instant dataHora) {

    public static Transacao debito(Cliente cliente, Mensagem mensagem, Instant agora) {
        return nova(cliente, mensagem.id(), TipoTransacao.DEBITO, mensagem.custo(), agora);
    }

    public static Transacao estorno(Cliente cliente, Mensagem mensagem, Instant agora) {
        return nova(cliente, mensagem.id(), TipoTransacao.ESTORNO, mensagem.custo(), agora);
    }

    public static Transacao credito(Cliente cliente, BigDecimal valor, Instant agora) {
        return nova(cliente, null, TipoTransacao.CREDITO, valor, agora);
    }

    public static Transacao mensalidade(Cliente cliente, BigDecimal valor, Instant agora) {
        return nova(cliente, null, TipoTransacao.MENSALIDADE, valor, agora);
    }

    public static Transacao ajusteDeLimite(Cliente cliente, Instant agora) {
        return nova(cliente, null, TipoTransacao.AJUSTE_LIMITE, cliente.limiteMensal(), agora);
    }

    private static Transacao nova(Cliente cliente, UUID mensagemId, TipoTransacao tipo, BigDecimal valor,
                                  Instant agora) {
        return new Transacao(UUID.randomUUID(), cliente.id(), mensagemId, tipo, valor, cliente.disponivel(), agora);
    }
}
