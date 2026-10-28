package br.com.archat.aplicacao.mensagem;

import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.EnvioMensagemGateway;
import br.com.archat.aplicacao.porta.FilaMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.MetricasFila;
import br.com.archat.aplicacao.porta.TransacaoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.financeiro.Transacao;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.StatusMensagem;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Processa uma mensagem retirada da fila: tenta enviá-la e, em caso de falha, devolve-a à fila
 * ou, esgotadas as tentativas, marca a falha e estorna o valor cobrado.
 */
public class ProcessarMensagemUseCase {

    private final MensagemRepositorio mensagens;
    private final ConversaRepositorio conversas;
    private final DestinatarioRepositorio destinatarios;
    private final ClienteRepositorio clientes;
    private final TransacaoRepositorio transacoes;
    private final FilaMensagens fila;
    private final EnvioMensagemGateway envio;
    private final MetricasFila metricas;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;
    private final int maximoTentativas;

    public ProcessarMensagemUseCase(MensagemRepositorio mensagens, ConversaRepositorio conversas,
                                    DestinatarioRepositorio destinatarios, ClienteRepositorio clientes, TransacaoRepositorio transacoes,
                                    FilaMensagens fila, EnvioMensagemGateway envio, MetricasFila metricas,
                                    UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio, int maximoTentativas) {
        this.mensagens = mensagens;
        this.conversas = conversas;
        this.destinatarios = destinatarios;
        this.clientes = clientes;
        this.transacoes = transacoes;
        this.fila = fila;
        this.envio = envio;
        this.metricas = metricas;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
        this.maximoTentativas = maximoTentativas;
    }

    public Optional<StatusMensagem> executar(UUID mensagemId) {
        Optional<Mensagem> emProcessamento = iniciarProcessamento(mensagemId);
        if (emProcessamento.isEmpty()) {
            return Optional.empty();
        }
        Mensagem mensagem = emProcessamento.get();
        Duration esperaNaFila = Duration.between(mensagem.criadaEm(), relogio.instant());
        EnvioMensagemGateway.ResultadoEnvio resultado = tentarEnviar(mensagem);
        if (resultado.aceito()) {
            metricas.registrarEnvio(esperaNaFila);
            return Optional.of(confirmarEnvio(mensagemId, resultado.idExterno()));
        }
        return Optional.of(registrarFalha(mensagemId));
    }

    private Optional<Mensagem> iniciarProcessamento(UUID mensagemId) {
        return unidadeDeTrabalho.executar(() -> mensagens.buscarPorId(mensagemId)
                .filter(m -> m.status() == StatusMensagem.ENFILEIRADA)
                .map(m -> {
                    m.iniciarProcessamento(relogio.instant());
                    mensagens.salvar(m);
                    return m;
                }));
    }

    private EnvioMensagemGateway.ResultadoEnvio tentarEnviar(Mensagem mensagem) {
        try {
            Conversa conversa = conversas.buscarPorId(mensagem.conversaId())
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa"));
            return envio.enviar(new EnvioMensagemGateway.Envio(mensagem.id(), conversa.clienteId(),
                    telefoneDoDestinatario(conversa), mensagem.conteudo(), mensagem.midia(), mensagem.prioridade(),
                    mensagem.tentativas(), mensagem.contato()));
        } catch (RuntimeException erro) {
            return EnvioMensagemGateway.ResultadoEnvio.recusado();
        }
    }

    private String telefoneDoDestinatario(Conversa conversa) {
        return destinatarios.buscarPorId(conversa.destinatarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Destinatário"))
                .telefone();
    }

    private StatusMensagem confirmarEnvio(UUID mensagemId, String idExterno) {
        return unidadeDeTrabalho.executar(() -> {
            Mensagem mensagem = buscar(mensagemId);
            mensagem.confirmarEnvio(idExterno, relogio.instant());
            mensagens.salvar(mensagem);
            return mensagem.status();
        });
    }

    private StatusMensagem registrarFalha(UUID mensagemId) {
        Mensagem mensagem = unidadeDeTrabalho.executar(() -> {
            Mensagem m = buscar(mensagemId);
            m.registrarFalhaDeEnvio(maximoTentativas, relogio.instant());
            mensagens.salvar(m);
            if (m.falhouDefinitivamente()) {
                estornar(m);
            }
            return m;
        });
        if (mensagem.falhouDefinitivamente()) {
            metricas.registrarFalhaDefinitiva();
        } else {
            metricas.registrarFalhaTemporaria();
            fila.enfileirar(mensagem.id(), mensagem.prioridade());
        }
        return mensagem.status();
    }

    private void estornar(Mensagem mensagem) {
        UUID clienteId = conversas.buscarPorId(mensagem.conversaId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa"))
                .clienteId();
        Cliente cliente = clientes.buscarPorIdParaAtualizacao(clienteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Cliente"));
        cliente.estornar(mensagem.custo());
        clientes.salvar(cliente);
        transacoes.salvar(Transacao.estorno(cliente, mensagem, relogio.instant()));
    }

    private Mensagem buscar(UUID mensagemId) {
        return mensagens.buscarPorId(mensagemId).orElseThrow(() -> new RecursoNaoEncontradoException("Mensagem"));
    }
}
