package br.com.archat.aplicacao.atendimento;

import br.com.archat.aplicacao.porta.AtendimentoRepositorio;
import br.com.archat.aplicacao.integracao.DadosDosEventos;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.aplicacao.porta.NotificadorDeIntegracao;
import br.com.archat.dominio.integracao.EventoDeIntegracao;
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
import java.util.LinkedHashMap;
import java.util.Optional;
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
    private NotificadorDeIntegracao integracoes = NotificadorDeIntegracao.NENHUM;

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
            conversa.definirDepartamento(departamentoId != null ? departamentoId
                    : departamentos.garantirGeral(clienteId).id());
            conversas.salvar(conversa);
            return conversa;
        });
    }

    public Conversa atribuir(UUID clienteId, UUID conversaId, UUID operadorId, UUID solicitante) {
        return unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            Operador operador = null;
            if (operadorId != null) {
                operador = operadores.buscarPorId(operadorId)
                        .filter(o -> o.pertenceA(clienteId) && o.ativo())
                        .orElseThrow(() -> new RegraDeNegocioException("Operador inválido ou desativado"));
            }
            Operador atendente = operador;
            if (solicitante != null && conversa.operadorId() != null && !conversa.operadorId().equals(solicitante)
                    && !solicitante.equals(operadorId)) {
                throw new RegraDeNegocioException("Esta conversa está com outro operador");
            }
            conversa.atribuirA(operadorId);
            conversas.salvar(conversa);
            if (operadorId != null) {
                atendimentos.buscarAbertoDaConversa(conversaId).ifPresent(a -> {
                    a.iniciar(relogio.instant());
                    atendimentos.salvar(a);
                    Map<String, Object> dados = dadosDoAtendimento(a, conversa, atendente.nome());
                    dados.put("startedAt", relogio.instant().toString());
                    integracoes.notificar(clienteId, EventoDeIntegracao.ATENDIMENTO_INICIADO, dados);
                });
            }
            return conversa;
        });
    }

    public Optional<Atendimento> cicloAtual(UUID conversaId) {
        return atendimentos.buscarUltimoDaConversa(conversaId);
    }

    public Conversa colocarEmEspera(UUID clienteId, UUID conversaId) {
        return alterar(clienteId, conversaId, Atendimento::colocarEmEspera);
    }

    public Conversa retomar(UUID clienteId, UUID conversaId) {
        return alterar(clienteId, conversaId, Atendimento::retomar);
    }

    public Conversa finalizar(UUID clienteId, UUID conversaId, String observacao, UUID autorId) {
        return unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            Atendimento atendimento = atendimentos.buscarAbertoDaConversa(conversaId)
                    .orElseThrow(() -> new RegraDeNegocioException("Esta conversa não tem atendimento aberto"));
            atendimento.finalizar(relogio.instant(), observacao);
            atendimentos.salvar(atendimento);
            conversa.atualizarStatusAtendimento(atendimento.status());
            conversas.salvar(conversa);
            if (atendimento.observacaoDeEncerramento() != null) {
                registrarNota(conversaId, autorId, "Observação do encerramento: " + atendimento.observacaoDeEncerramento());
            }
            String nomeDoOperador = conversa.operadorId() == null || operadores == null ? null
                    : operadores.buscarPorId(conversa.operadorId()).map(Operador::nome).orElse(null);
            Map<String, Object> dados = dadosDoAtendimento(atendimento, conversa, nomeDoOperador);
            dados.put("startedAt", atendimento.iniciadoEm() == null ? null : atendimento.iniciadoEm().toString());
            dados.put("finishedAt", relogio.instant().toString());
            dados.put("closingNote", atendimento.observacaoDeEncerramento());
            integracoes.notificar(clienteId, EventoDeIntegracao.ATENDIMENTO_FINALIZADO, dados);
            return conversa;
        });
    }

    public Conversa enviarParaGeral(UUID clienteId, UUID conversaId, String observacao, UUID autorId) {
        String limpa = observacao == null ? "" : observacao.strip();
        if (limpa.length() > Atendimento.TAMANHO_MAXIMO_OBSERVACAO) {
            throw new RegraDeNegocioException("A observação deve ter no máximo %d caracteres"
                    .formatted(Atendimento.TAMANHO_MAXIMO_OBSERVACAO));
        }
        return unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            Atendimento atendimento = atendimentos.buscarAbertoDaConversa(conversaId)
                    .orElseThrow(() -> new RegraDeNegocioException("Esta conversa não tem atendimento aberto"));
            atendimento.devolverParaFila();
            atendimentos.salvar(atendimento);
            conversa.definirDepartamento(departamentos.garantirGeral(clienteId).id());
            conversa.atribuirA(null);
            conversa.atualizarStatusAtendimento(atendimento.status());
            conversas.salvar(conversa);
            registrarNota(conversaId, autorId, "Enviado para o geral" + (limpa.isEmpty() ? "" : " · Observação: " + limpa));
            return conversa;
        });
    }

    public GerenciarAtendimentoUseCase comIntegracoes(NotificadorDeIntegracao notificador) {
        this.integracoes = notificador == null ? NotificadorDeIntegracao.NENHUM : notificador;
        return this;
    }

    private Map<String, Object> dadosDoAtendimento(Atendimento atendimento, Conversa conversa, String nomeDoOperador) {
        Map<String, Object> dados = new LinkedHashMap<>();
        dados.put("attendanceId", atendimento.id().toString());
        dados.put("conversationId", conversa.id().toString());
        Destinatario contato = destinatarios == null ? null : destinatarios.buscarPorId(conversa.destinatarioId()).orElse(null);
        dados.put("contact", contato == null ? null : DadosDosEventos.contato(contato.nome(), contato.telefone()));
        Map<String, Object> operador = new LinkedHashMap<>();
        operador.put("id", conversa.operadorId() == null ? null : conversa.operadorId().toString());
        operador.put("name", nomeDoOperador);
        dados.put("operator", conversa.operadorId() == null ? null : operador);
        String departamento = conversa.departamentoId() == null || departamentos == null ? null
                : departamentos.buscarPorId(conversa.departamentoId()).map(d -> d.nome()).orElse(null);
        dados.put("department", departamento);
        return dados;
    }

    private void registrarNota(UUID conversaId, UUID autorId, String texto) {
        if (mensagens != null) {
            mensagens.salvar(Mensagem.notaInterna(conversaId, texto, autorId, relogio.instant()));
        }
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
