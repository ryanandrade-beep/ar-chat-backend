package br.com.archat.aplicacao.relatorio;

import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.MensagemRepositorio.ContagemPorCliente;
import br.com.archat.aplicacao.porta.TransacaoRepositorio;
import br.com.archat.aplicacao.porta.TransacaoRepositorio.TotalPorCliente;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.TipoPlano;
import br.com.archat.dominio.financeiro.TipoTransacao;
import br.com.archat.dominio.mensagem.Prioridade;
import br.com.archat.dominio.mensagem.StatusMensagem;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Faturamento da plataforma no período, por cliente, do que mais gerou receita para o que menos gerou.
 * Receita líquida = valor debitado pelas mensagens − valor estornado por falhas.
 */
public class RelatorioFaturamentoUseCase {

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);
    private static final BigDecimal CEM = BigDecimal.valueOf(100);

    private final ClienteRepositorio clientes;
    private final TransacaoRepositorio transacoes;
    private final MensagemRepositorio mensagens;

    public RelatorioFaturamentoUseCase(ClienteRepositorio clientes, TransacaoRepositorio transacoes,
                                       MensagemRepositorio mensagens) {
        this.clientes = clientes;
        this.transacoes = transacoes;
        this.mensagens = mensagens;
    }

    public Faturamento emitir(Periodo periodo) {
        return emitir(periodo, Filtro.NENHUM);
    }

    public Faturamento emitir(Periodo periodo, Filtro filtro) {
        List<TotalPorCliente> financeiro = transacoes.totalizarPorCliente(periodo.de(), periodo.ate());
        List<ContagemPorCliente> contagens = mensagens.contarPorCliente(periodo.de(), periodo.ate());
        Map<UUID, Cliente> clientesPorId = clientes.listarTodos().stream()
                .collect(Collectors.toMap(Cliente::id, Function.identity()));

        Set<UUID> ativos = new HashSet<>();
        contagens.forEach(c -> ativos.add(c.clienteId()));
        financeiro.stream().filter(f -> f.tipo() == TipoTransacao.DEBITO).forEach(f -> ativos.add(f.clienteId()));

        List<LinhaSemParticipacao> linhas = ativos.stream()
                .filter(clientesPorId::containsKey)
                .filter(id -> filtro.plano() == null || clientesPorId.get(id).tipoPlano() == filtro.plano())
                .filter(id -> RelatorioAtendimentosUseCase.contem(clientesPorId.get(id).nome(), filtro.busca()))
                .map(id -> linha(clientesPorId.get(id), financeiro, contagens))
                .toList();
        BigDecimal receitaTotal = linhas.stream().map(LinhaSemParticipacao::receitaLiquida).reduce(ZERO, BigDecimal::add);

        List<FaturamentoDoCliente> resultado = linhas.stream()
                .map(l -> l.comParticipacao(percentual(l.receitaLiquida(), receitaTotal)))
                .sorted(Comparator.comparing(FaturamentoDoCliente::receitaLiquida).reversed()
                        .thenComparing(FaturamentoDoCliente::nome))
                .toList();

        return new Faturamento(periodo, resultado,
                resultado.stream().mapToLong(FaturamentoDoCliente::mensagens).sum(),
                resultado.stream().map(FaturamentoDoCliente::receitaBruta).reduce(ZERO, BigDecimal::add),
                resultado.stream().map(FaturamentoDoCliente::estornos).reduce(ZERO, BigDecimal::add),
                receitaTotal);
    }

    private static LinhaSemParticipacao linha(Cliente cliente, List<TotalPorCliente> financeiro,
                                              List<ContagemPorCliente> contagens) {
        List<ContagemPorCliente> doCliente = contagens.stream().filter(c -> c.clienteId().equals(cliente.id())).toList();
        long total = doCliente.stream().mapToLong(ContagemPorCliente::quantidade).sum();
        long urgentes = doCliente.stream().filter(c -> c.prioridade() == Prioridade.URGENTE)
                .mapToLong(ContagemPorCliente::quantidade).sum();
        long cobradas = doCliente.stream().filter(c -> c.status() != StatusMensagem.FALHA)
                .mapToLong(ContagemPorCliente::quantidade).sum();
        BigDecimal bruta = somar(financeiro, cliente.id(), TipoTransacao.DEBITO);
        BigDecimal estornos = somar(financeiro, cliente.id(), TipoTransacao.ESTORNO);
        BigDecimal liquida = bruta.subtract(estornos);
        BigDecimal ticketMedio = cobradas == 0 ? ZERO
                : liquida.divide(BigDecimal.valueOf(cobradas), 2, RoundingMode.HALF_EVEN);
        return new LinhaSemParticipacao(cliente.id(), cliente.nome(), cliente.tipoPlano(), total, urgentes,
                percentual(BigDecimal.valueOf(urgentes), BigDecimal.valueOf(total)), bruta, estornos, liquida, ticketMedio);
    }

    private static BigDecimal somar(List<TotalPorCliente> financeiro, UUID clienteId, TipoTransacao tipo) {
        return financeiro.stream()
                .filter(f -> f.clienteId().equals(clienteId) && f.tipo() == tipo)
                .map(TotalPorCliente::valor)
                .reduce(ZERO, BigDecimal::add);
    }

    private static BigDecimal percentual(BigDecimal parte, BigDecimal todo) {
        return todo.signum() == 0 ? ZERO : parte.multiply(CEM).divide(todo, 2, RoundingMode.HALF_EVEN);
    }

    public record Filtro(TipoPlano plano, String busca) {

        public static final Filtro NENHUM = new Filtro(null, null);
    }

    private record LinhaSemParticipacao(UUID clienteId, String nome, TipoPlano tipoPlano, long mensagens,
                                        long urgentes, BigDecimal percentualUrgentes, BigDecimal receitaBruta,
                                        BigDecimal estornos, BigDecimal receitaLiquida, BigDecimal ticketMedio) {

        FaturamentoDoCliente comParticipacao(BigDecimal participacao) {
            return new FaturamentoDoCliente(clienteId, nome, tipoPlano, mensagens, urgentes, percentualUrgentes,
                    receitaBruta, estornos, receitaLiquida, ticketMedio, participacao);
        }
    }

    public record FaturamentoDoCliente(UUID clienteId, String nome, TipoPlano tipoPlano, long mensagens,
                                       long urgentes, BigDecimal percentualUrgentes, BigDecimal receitaBruta,
                                       BigDecimal estornos, BigDecimal receitaLiquida, BigDecimal ticketMedio,
                                       BigDecimal participacao) {
    }

    public record Faturamento(Periodo periodo, List<FaturamentoDoCliente> clientes, long totalMensagens,
                              BigDecimal receitaBruta, BigDecimal estornos, BigDecimal receitaLiquida) {
    }
}
