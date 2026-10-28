package br.com.archat.aplicacao.plano;

import br.com.archat.aplicacao.porta.AssinaturaRepositorio;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.aplicacao.porta.PlanoRepositorio;
import br.com.archat.aplicacao.porta.TransacaoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.excecao.SaldoInsuficienteException;
import br.com.archat.dominio.financeiro.Transacao;
import br.com.archat.dominio.plano.Assinatura;
import br.com.archat.dominio.plano.Plano;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Backoffice: cria e altera planos, vincula workspaces a um plano e cobra as mensalidades. A mensalidade
 * do primeiro mês é cobrada ao assinar; as seguintes, no início de cada mês. Sem saldo (pré-pago) ou limite
 * (pós-pago), a assinatura fica em atraso, mas o histórico e o envio continuam conforme o saldo.
 */
public class GerenciarPlanosUseCase {

    private final PlanoRepositorio planos;
    private final AssinaturaRepositorio assinaturas;
    private final ClienteRepositorio clientes;
    private final TransacaoRepositorio transacoes;
    private final OperadorRepositorio operadores;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public GerenciarPlanosUseCase(PlanoRepositorio planos, AssinaturaRepositorio assinaturas, ClienteRepositorio clientes,
                                  TransacaoRepositorio transacoes, OperadorRepositorio operadores,
                                  UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.planos = planos;
        this.assinaturas = assinaturas;
        this.clientes = clientes;
        this.transacoes = transacoes;
        this.operadores = operadores;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public List<Plano> listar() {
        return planos.listarTodos().stream().sorted(Comparator.comparing(Plano::mensalidade)).toList();
    }

    public Plano criar(DadosPlano d) {
        Plano plano = Plano.novo(d.nome(), d.mensalidade(), d.operadoresIncluidos(), d.precoNormal(), d.precoUrgente(),
                relogio.instant());
        return unidadeDeTrabalho.executar(() -> {
            if (planos.existeComNome(plano.nome(), null)) {
                throw new RegraDeNegocioException("Já existe um plano com este nome");
            }
            planos.salvar(plano);
            return plano;
        });
    }

    public Plano alterar(UUID planoId, DadosPlano d, boolean ativo) {
        return unidadeDeTrabalho.executar(() -> {
            Plano plano = buscar(planoId);
            plano.alterar(d.nome(), d.mensalidade(), d.operadoresIncluidos(), d.precoNormal(), d.precoUrgente(), ativo);
            if (planos.existeComNome(plano.nome(), plano.id())) {
                throw new RegraDeNegocioException("Já existe um plano com este nome");
            }
            planos.salvar(plano);
            return plano;
        });
    }

    public Plano buscar(UUID planoId) {
        return planos.buscarPorId(planoId).orElseThrow(() -> new RecursoNaoEncontradoException("Plano"));
    }

    public Optional<Assinatura> assinaturaDe(UUID clienteId) {
        return assinaturas.buscarPorCliente(clienteId);
    }

    public Assinatura assinar(UUID clienteId, UUID planoId) {
        Instant agora = relogio.instant();
        return unidadeDeTrabalho.executar(() -> {
            Plano plano = buscar(planoId);
            if (!plano.ativo()) {
                throw new RegraDeNegocioException("Plano inativo não pode receber novas assinaturas");
            }
            long ativos = operadores.listarPorCliente(clienteId).stream().filter(o -> o.ativo() && !o.dono()).count();
            if (ativos > plano.operadoresIncluidos()) {
                throw new RegraDeNegocioException("A workspace tem %d operadores ativos; o plano inclui %d"
                        .formatted(ativos, plano.operadoresIncluidos()));
            }
            Cliente cliente = clientes.buscarPorIdParaAtualizacao(clienteId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente"));
            Assinatura assinatura = assinaturas.buscarPorCliente(clienteId)
                    .map(a -> { a.trocarPlano(planoId, agora); return a; })
                    .orElseGet(() -> Assinatura.nova(clienteId, planoId, agora));
            cobrarMensalidade(cliente, plano, assinatura, agora, true);
            assinaturas.salvar(assinatura);
            return assinatura;
        });
    }

    public int cobrarMensalidades() {
        Instant agora = relogio.instant();
        int cobradas = 0;
        for (Assinatura a : assinaturas.listarTodas()) {
            boolean ok = unidadeDeTrabalho.executar(() -> {
                Assinatura assinatura = assinaturas.buscarPorCliente(a.clienteId()).orElseThrow();
                Plano plano = buscar(assinatura.planoId());
                Cliente cliente = clientes.buscarPorIdParaAtualizacao(assinatura.clienteId()).orElseThrow();
                boolean cobrou = cobrarMensalidade(cliente, plano, assinatura, agora, false);
                assinaturas.salvar(assinatura);
                return cobrou;
            });
            cobradas += ok ? 1 : 0;
        }
        return cobradas;
    }

    private boolean cobrarMensalidade(Cliente cliente, Plano plano, Assinatura assinatura, Instant agora, boolean exigir) {
        BigDecimal valor = plano.mensalidade();
        if (valor.signum() == 0) {
            assinatura.registrarCobranca(agora);
            return true;
        }
        try {
            cliente.cobrar(valor);
        } catch (SaldoInsuficienteException | RegraDeNegocioException e) {
            if (exigir) {
                throw new RegraDeNegocioException("Saldo ou limite insuficiente para a mensalidade de R$ " + valor
                        + " — adicione crédito antes de assinar o plano");
            }
            assinatura.registrarAtraso();
            return false;
        }
        clientes.salvar(cliente);
        transacoes.salvar(Transacao.mensalidade(cliente, valor, agora));
        assinatura.registrarCobranca(agora);
        return true;
    }

    public record DadosPlano(String nome, BigDecimal mensalidade, int operadoresIncluidos, BigDecimal precoNormal,
                             BigDecimal precoUrgente) {
    }
}
