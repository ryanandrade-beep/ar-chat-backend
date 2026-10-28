package br.com.archat.aplicacao.mensagem;

import br.com.archat.aplicacao.atendimento.RegistroDeAtendimento;
import br.com.archat.aplicacao.plano.PlanoDoCliente;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.FilaMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.TransacaoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.financeiro.Transacao;
import br.com.archat.dominio.mensagem.ContatoCompartilhado;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Midia;
import br.com.archat.dominio.mensagem.Prioridade;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Cobra a mensagem e a coloca na fila. A cobrança, a transação e a mensagem são gravadas juntas;
 * a mensagem só entra na fila depois disso, para o worker nunca ver uma mensagem que não existe no banco.
 */
public class EnviarMensagemUseCase {

    private final ClienteRepositorio clientes;
    private final DestinatarioRepositorio destinatarios;
    private final ConversaRepositorio conversas;
    private final MensagemRepositorio mensagens;
    private final TransacaoRepositorio transacoes;
    private final FilaMensagens fila;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;
    private final Duration tempoEstimadoPorMensagem;
    private final RegistroDeAtendimento atendimentos;
    private final PlanoDoCliente planos;

    public EnviarMensagemUseCase(ClienteRepositorio clientes, DestinatarioRepositorio destinatarios,
                                 ConversaRepositorio conversas, MensagemRepositorio mensagens,
                                 TransacaoRepositorio transacoes, FilaMensagens fila,
                                 UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio,
                                 Duration tempoEstimadoPorMensagem, RegistroDeAtendimento atendimentos) {
        this(clientes, destinatarios, conversas, mensagens, transacoes, fila, unidadeDeTrabalho, relogio,
                tempoEstimadoPorMensagem, atendimentos, null);
    }

    public EnviarMensagemUseCase(ClienteRepositorio clientes, DestinatarioRepositorio destinatarios,
                                 ConversaRepositorio conversas, MensagemRepositorio mensagens,
                                 TransacaoRepositorio transacoes, FilaMensagens fila,
                                 UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio,
                                 Duration tempoEstimadoPorMensagem, RegistroDeAtendimento atendimentos,
                                 PlanoDoCliente planos) {
        this.planos = planos;
        this.clientes = clientes;
        this.destinatarios = destinatarios;
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.transacoes = transacoes;
        this.fila = fila;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
        this.tempoEstimadoPorMensagem = tempoEstimadoPorMensagem;
        this.atendimentos = atendimentos;
    }

    public MensagemEnviada executar(Comando comando) {
        Instant agora = relogio.instant();
        MensagemEnviada enviada = unidadeDeTrabalho.executar(() -> {
            Conversa conversa = conversaDoEnvio(comando);
            Cliente cliente = clientes.buscarPorIdParaAtualizacao(comando.clienteId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente"));
            BigDecimal preco = comando.prioridade() == null ? null
                    : planos == null ? comando.prioridade().custo() : planos.custo(cliente.id(), comando.prioridade());
            Mensagem mensagem = comando.contato() != null
                    ? Mensagem.compartilhandoContato(conversa.id(), comando.contato(), comando.prioridade(), preco, agora)
                    : Mensagem.novaDoCliente(conversa.id(), null, comando.conteudo(), comando.midia(),
                            comando.prioridade(), preco, agora);

            mensagem.escritaPor(comando.operadorId());
            cliente.cobrar(mensagem.custo());
            conversa.registrar(mensagem);
            conversa.assumirSeLivre(comando.operadorId());

            clientes.salvar(cliente);
            conversas.salvar(conversa);
            atendimentos.aoResponder(conversa, agora);
            conversas.salvar(conversa);
            mensagens.salvar(mensagem);
            transacoes.salvar(Transacao.debito(cliente, mensagem, agora));
            return new MensagemEnviada(mensagem, cliente.disponivel(), previsaoDeEntrega(agora));
        });
        fila.enfileirar(enviada.mensagem().id(), enviada.mensagem().prioridade());
        return enviada;
    }

    private Conversa conversaDoEnvio(Comando comando) {
        if (comando.conversaId() != null) {
            return conversas.buscarPorId(comando.conversaId())
                    .filter(c -> c.pertenceA(comando.clienteId()))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa"));
        }
        if (comando.destinatarioId() == null) {
            throw new RegraDeNegocioException("Informe a conversa ou o destinatário da mensagem");
        }
        Destinatario destinatario = destinatarios.buscarPorId(comando.destinatarioId())
                .filter(d -> d.pertenceA(comando.clienteId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Destinatário"));
        return conversas.buscarPorDestinatario(destinatario.id())
                .orElseGet(() -> Conversa.iniciar(comando.clienteId(), destinatario.id()));
    }

    private Instant previsaoDeEntrega(Instant agora) {
        return agora.plus(tempoEstimadoPorMensagem.multipliedBy(fila.tamanho() + 1L));
    }

    public record Comando(UUID clienteId, UUID conversaId, UUID destinatarioId, String conteudo, Midia midia,
                          Prioridade prioridade, UUID operadorId, ContatoCompartilhado contato) {

        public Comando(UUID clienteId, UUID conversaId, UUID destinatarioId, String conteudo, Midia midia,
                       Prioridade prioridade, UUID operadorId) {
            this(clienteId, conversaId, destinatarioId, conteudo, midia, prioridade, operadorId, null);
        }

        public Comando(UUID clienteId, UUID conversaId, UUID destinatarioId, String conteudo, Midia midia,
                       Prioridade prioridade) {
            this(clienteId, conversaId, destinatarioId, conteudo, midia, prioridade, null, null);
        }

        public Comando(UUID clienteId, UUID conversaId, UUID destinatarioId, String conteudo, Prioridade prioridade) {
            this(clienteId, conversaId, destinatarioId, conteudo, null, prioridade, null, null);
        }
    }

    public record MensagemEnviada(Mensagem mensagem, BigDecimal disponivel, Instant previsaoEntrega) {
    }
}
