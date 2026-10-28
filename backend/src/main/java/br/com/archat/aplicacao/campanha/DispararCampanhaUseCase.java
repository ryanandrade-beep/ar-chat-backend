package br.com.archat.aplicacao.campanha;

import br.com.archat.aplicacao.plano.PlanoDoCliente;
import br.com.archat.aplicacao.porta.CampanhaRepositorio;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.FilaMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.TransacaoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.campanha.Campanha;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.excecao.SaldoInsuficienteException;
import br.com.archat.dominio.financeiro.Transacao;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Midia;
import br.com.archat.dominio.mensagem.Prioridade;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Dispara a mesma mensagem para vários destinatários. A cobrança é tudo ou nada: se o saldo/limite
 * não cobre a campanha inteira, nada é cobrado nem enfileirado. Só recebem campanha os contatos
 * que aceitaram receber ofertas (LGPD).
 */
public class DispararCampanhaUseCase {

    private final ClienteRepositorio clientes;
    private final DestinatarioRepositorio destinatarios;
    private final ConversaRepositorio conversas;
    private final MensagemRepositorio mensagens;
    private final TransacaoRepositorio transacoes;
    private final CampanhaRepositorio campanhas;
    private final FilaMensagens fila;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;
    private final PlanoDoCliente planos;

    public DispararCampanhaUseCase(ClienteRepositorio clientes, DestinatarioRepositorio destinatarios,
                                   ConversaRepositorio conversas, MensagemRepositorio mensagens,
                                   TransacaoRepositorio transacoes, CampanhaRepositorio campanhas,
                                   FilaMensagens fila, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this(clientes, destinatarios, conversas, mensagens, transacoes, campanhas, fila, unidadeDeTrabalho, relogio, null);
    }

    public DispararCampanhaUseCase(ClienteRepositorio clientes, DestinatarioRepositorio destinatarios,
                                   ConversaRepositorio conversas, MensagemRepositorio mensagens,
                                   TransacaoRepositorio transacoes, CampanhaRepositorio campanhas,
                                   FilaMensagens fila, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio,
                                   PlanoDoCliente planos) {
        this.planos = planos;
        this.clientes = clientes;
        this.destinatarios = destinatarios;
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.transacoes = transacoes;
        this.campanhas = campanhas;
        this.fila = fila;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public CampanhaDisparada executar(Comando comando) {
        Instant agora = relogio.instant();
        CampanhaDisparada disparada = unidadeDeTrabalho.executar(() -> {
            Cliente cliente = clientes.buscarPorIdParaAtualizacao(comando.clienteId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente"));
            List<Destinatario> publico = publicoDaCampanha(comando);
            BigDecimal preco = comando.prioridade() == null ? null
                    : planos == null ? comando.prioridade().custo() : planos.custo(cliente.id(), comando.prioridade());
            Campanha campanha = Campanha.nova(cliente.id(), comando.nome(), comando.conteudo(), comando.midia(),
                    comando.prioridade(), preco,
                    publico.size(), agora);
            garantirSaldoParaTudo(cliente, campanha);

            campanhas.salvar(campanha);
            List<Mensagem> criadas = new ArrayList<>();
            for (Destinatario destinatario : publico) {
                criadas.add(registrarMensagem(cliente, campanha, destinatario, agora));
            }
            clientes.salvar(cliente);
            return new CampanhaDisparada(campanha, criadas, cliente.disponivel());
        });
        disparada.mensagens().forEach(m -> fila.enfileirar(m.id(), m.prioridade()));
        return disparada;
    }

    private List<Destinatario> publicoDaCampanha(Comando comando) {
        if (comando.todosQueAceitamOfertas()) {
            return destinatarios.listarQueAceitamOfertas(comando.clienteId());
        }
        List<Destinatario> escolhidos = new LinkedHashSet<>(comando.destinatarioIds() == null ? List.<UUID>of()
                : comando.destinatarioIds()).stream()
                .map(id -> destinatarios.buscarPorId(id)
                        .filter(d -> d.pertenceA(comando.clienteId()))
                        .orElseThrow(() -> new RecursoNaoEncontradoException("Destinatário " + id)))
                .toList();
        long semConsentimento = escolhidos.stream().filter(d -> !d.aceitaOfertas()).count();
        if (semConsentimento > 0) {
            throw new RegraDeNegocioException(
                    "%d destinatário(s) não aceitam receber ofertas (LGPD) e não podem receber campanhas"
                            .formatted(semConsentimento));
        }
        return escolhidos;
    }

    private static void garantirSaldoParaTudo(Cliente cliente, Campanha campanha) {
        BigDecimal disponivel = cliente.disponivel();
        if (disponivel.compareTo(campanha.custoTotal()) < 0) {
            throw new SaldoInsuficienteException("Saldo/limite insuficiente para a campanha inteira", disponivel,
                    campanha.custoTotal());
        }
    }

    private Mensagem registrarMensagem(Cliente cliente, Campanha campanha, Destinatario destinatario, Instant agora) {
        Conversa conversa = conversas.buscarPorDestinatario(destinatario.id())
                .orElseGet(() -> Conversa.iniciar(cliente.id(), destinatario.id()));
        BigDecimal preco = campanha.custoTotal().divide(BigDecimal.valueOf(campanha.totalDestinatarios()), 2,
                RoundingMode.HALF_EVEN);
        Mensagem mensagem = Mensagem.novaDoCliente(conversa.id(), campanha.id(), campanha.conteudo(),
                campanha.midia(), campanha.prioridade(), preco, agora);
        cliente.cobrar(mensagem.custo());
        conversa.registrar(mensagem);
        conversas.salvar(conversa);
        mensagens.salvar(mensagem);
        transacoes.salvar(Transacao.debito(cliente, mensagem, agora));
        return mensagem;
    }

    public record Comando(UUID clienteId, String nome, String conteudo, Midia midia, Prioridade prioridade,
                          List<UUID> destinatarioIds, boolean todosQueAceitamOfertas) {

        public Comando(UUID clienteId, String nome, String conteudo, Prioridade prioridade,
                       List<UUID> destinatarioIds, boolean todosQueAceitamOfertas) {
            this(clienteId, nome, conteudo, null, prioridade, destinatarioIds, todosQueAceitamOfertas);
        }
    }

    public record CampanhaDisparada(Campanha campanha, List<Mensagem> mensagens, BigDecimal disponivel) {
    }
}
