package br.com.archat.aplicacao.atendimento;

import br.com.archat.aplicacao.porta.AtendimentoRepositorio;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DepartamentoRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.FiltroMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.atendimento.StatusAtendimento;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Remetente;

import java.time.Clock;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/** Ações da empresa sobre o atendimento: colocar em espera, retomar, finalizar e marcar como lido. */
public class GerenciarAtendimentoUseCase {

    private final ConversaRepositorio conversas;
    private final AtendimentoRepositorio atendimentos;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;
    private final MensagemRepositorio mensagens;
    private final DestinatarioRepositorio destinatarios;
    private final ConexaoWhatsApp canal;
    private final OperadorRepositorio operadores;
    private final DepartamentoRepositorio departamentos;

    public GerenciarAtendimentoUseCase(ConversaRepositorio conversas, AtendimentoRepositorio atendimentos,
                                       UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this(conversas, atendimentos, unidadeDeTrabalho, relogio, null, null, null, null, null);
    }

    public GerenciarAtendimentoUseCase(ConversaRepositorio conversas, AtendimentoRepositorio atendimentos,
                                       UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio,
                                       MensagemRepositorio mensagens, DestinatarioRepositorio destinatarios,
                                       ConexaoWhatsApp canal, OperadorRepositorio operadores,
                                       DepartamentoRepositorio departamentos) {
        this.conversas = conversas;
        this.atendimentos = atendimentos;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
        this.mensagens = mensagens;
        this.destinatarios = destinatarios;
        this.canal = canal;
        this.operadores = operadores;
        this.departamentos = departamentos;
    }

    public Conversa definirDepartamento(UUID clienteId, UUID conversaId, UUID departamentoId) {
        return unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            if (departamentoId != null) {
                departamentos.buscarPorId(departamentoId)
                        .filter(d -> d.pertenceA(clienteId) && d.ativo())
                        .orElseThrow(() -> new RegraDeNegocioException("Departamento inválido ou inativo"));
            }
            conversa.definirDepartamento(departamentoId);
            conversas.salvar(conversa);
            return conversa;
        });
    }

    public Conversa atribuir(UUID clienteId, UUID conversaId, UUID operadorId, UUID solicitante) {
        return unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            if (operadorId != null) {
                operadores.buscarPorId(operadorId)
                        .filter(o -> o.pertenceA(clienteId) && o.ativo())
                        .orElseThrow(() -> new RegraDeNegocioException("Operador inválido ou desativado"));
            }
            if (solicitante != null && conversa.operadorId() != null && !conversa.operadorId().equals(solicitante)
                    && !solicitante.equals(operadorId)) {
                throw new RegraDeNegocioException("Esta conversa está com outro operador");
            }
            conversa.atribuirA(operadorId);
            conversas.salvar(conversa);
            return conversa;
        });
    }

    public Conversa colocarEmEspera(UUID clienteId, UUID conversaId) {
        return alterar(clienteId, conversaId, Atendimento::colocarEmEspera);
    }

    public Conversa retomar(UUID clienteId, UUID conversaId) {
        return alterar(clienteId, conversaId, Atendimento::retomar);
    }

    public Conversa finalizar(UUID clienteId, UUID conversaId) {
        return alterar(clienteId, conversaId, atendimento -> atendimento.finalizar(relogio.instant()));
    }

    public Conversa marcarComoLida(UUID clienteId, UUID conversaId) {
        Conversa lida = unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            conversa.marcarComoLida();
            conversas.salvar(conversa);
            return conversa;
        });
        avisarLeituraAoCanal(clienteId, lida);
        return lida;
    }

    private void avisarLeituraAoCanal(UUID clienteId, Conversa conversa) {
        if (canal == null) {
            return;
        }
        try {
            mensagens.listar(new FiltroMensagens(clienteId, conversa.id(), null, null)).stream()
                    .filter(m -> m.remetente() == Remetente.DESTINATARIO && m.idExterno() != null)
                    .max(Comparator.comparing(Mensagem::criadaEm))
                    .ifPresent(ultima -> destinatarios.buscarPorId(conversa.destinatarioId())
                            .ifPresent(d -> canal.marcarComoLida(clienteId, d.telefone(), ultima.idExterno())));
        } catch (RuntimeException e) {
        }
    }

    public Map<StatusAtendimento, Long> resumo(UUID clienteId) {
        Map<StatusAtendimento, Long> resumo = new EnumMap<>(StatusAtendimento.class);
        for (StatusAtendimento status : StatusAtendimento.values()) {
            resumo.put(status, 0L);
        }
        conversas.listarPorCliente(clienteId).forEach(c -> resumo.merge(c.statusAtendimento(), 1L, Long::sum));
        return resumo;
    }

    private Conversa alterar(UUID clienteId, UUID conversaId, Consumer<Atendimento> acao) {
        return unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            Atendimento atendimento = atendimentos.buscarAbertoDaConversa(conversaId)
                    .orElseThrow(() -> new RegraDeNegocioException("Esta conversa não tem atendimento aberto"));
            acao.accept(atendimento);
            atendimentos.salvar(atendimento);
            conversa.atualizarStatusAtendimento(atendimento.status());
            conversas.salvar(conversa);
            return conversa;
        });
    }

    private Conversa daEmpresa(UUID clienteId, UUID conversaId) {
        return conversas.buscarPorId(conversaId)
                .filter(c -> c.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa"));
    }
}
