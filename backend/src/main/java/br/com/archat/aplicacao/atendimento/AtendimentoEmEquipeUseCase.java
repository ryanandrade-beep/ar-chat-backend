package br.com.archat.aplicacao.atendimento;

import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DepartamentoRepositorio;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.aplicacao.porta.ParticipanteRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.departamento.Departamento;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Mensagem;

import java.time.Clock;
import java.util.Set;
import java.util.UUID;

/**
 * Atendimento em equipe: participar de uma conversa que já tem responsável, trocar notas internas e
 * transferir para outro operador ou departamento deixando um recado.
 */
public class AtendimentoEmEquipeUseCase {

    private final ConversaRepositorio conversas;
    private final ParticipanteRepositorio participantes;
    private final OperadorRepositorio operadores;
    private final DepartamentoRepositorio departamentos;
    private final MensagemRepositorio mensagens;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public AtendimentoEmEquipeUseCase(ConversaRepositorio conversas, ParticipanteRepositorio participantes,
                                      OperadorRepositorio operadores, DepartamentoRepositorio departamentos,
                                      MensagemRepositorio mensagens, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.conversas = conversas;
        this.participantes = participantes;
        this.operadores = operadores;
        this.departamentos = departamentos;
        this.mensagens = mensagens;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public Set<UUID> participantes(UUID conversaId) {
        return participantes.participantes(conversaId);
    }

    public void participar(UUID clienteId, UUID conversaId, UUID operadorId) {
        unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            Operador operador = operadorDaEmpresa(clienteId, operadorId);
            if (operador.id().equals(conversa.operadorId())) {
                throw new RegraDeNegocioException("Você já é o responsável por esta conversa");
            }
            participantes.adicionar(conversaId, operadorId);
            mensagens.salvar(Mensagem.notaInterna(conversaId, operador.nome() + " entrou no atendimento", operadorId,
                    relogio.instant()));
        });
    }

    public void sair(UUID clienteId, UUID conversaId, UUID operadorId) {
        unidadeDeTrabalho.executar(() -> {
            daEmpresa(clienteId, conversaId);
            participantes.remover(conversaId, operadorId);
        });
    }

    public void exigirPodeResponder(UUID clienteId, UUID conversaId, UUID operadorId) {
        Conversa conversa = daEmpresa(clienteId, conversaId);
        if (conversa.operadorId() != null && !conversa.operadorId().equals(operadorId)
                && !participantes.participantes(conversaId).contains(operadorId)) {
            throw new RegraDeNegocioException("Esta conversa está com outro operador: clique em Participar para responder");
        }
    }

    public Mensagem notaInterna(UUID clienteId, UUID conversaId, UUID autorId, String texto) {
        return unidadeDeTrabalho.executar(() -> {
            daEmpresa(clienteId, conversaId);
            Mensagem nota = Mensagem.notaInterna(conversaId, texto, autorId, relogio.instant());
            mensagens.salvar(nota);
            return nota;
        });
    }

    public Conversa transferir(UUID clienteId, UUID conversaId, UUID operadorDestino, UUID departamentoDestino,
                               String recado, UUID autorId) {
        if (operadorDestino == null && departamentoDestino == null) {
            throw new RegraDeNegocioException("Escolha o operador ou o departamento de destino");
        }
        return unidadeDeTrabalho.executar(() -> {
            Conversa conversa = daEmpresa(clienteId, conversaId);
            StringBuilder texto = new StringBuilder("Transferido");
            if (departamentoDestino != null) {
                Departamento destino = departamentos.buscarPorId(departamentoDestino)
                        .filter(d -> d.pertenceA(clienteId) && d.ativo())
                        .orElseThrow(() -> new RegraDeNegocioException("Departamento inválido ou inativo"));
                conversa.definirDepartamento(destino.id());
                texto.append(" para o departamento ").append(destino.nome());
            }
            if (operadorDestino != null) {
                Operador destino = operadorDaEmpresa(clienteId, operadorDestino);
                if (!destino.ativo()) {
                    throw new RegraDeNegocioException("Operador desativado");
                }
                conversa.atribuirA(destino.id());
                participantes.remover(conversaId, destino.id());
                texto.append(departamentoDestino != null ? " e para " : " para ").append(destino.nome());
            }
            operadores.buscarPorId(autorId).ifPresent(autor -> texto.append(" por ").append(autor.nome()));
            if (recado != null && !recado.isBlank()) {
                texto.append(": ").append(recado.strip());
            }
            conversas.salvar(conversa);
            mensagens.salvar(Mensagem.notaInterna(conversaId, texto.toString(), autorId, relogio.instant()));
            return conversa;
        });
    }

    private Conversa daEmpresa(UUID clienteId, UUID conversaId) {
        return conversas.buscarPorId(conversaId).filter(c -> c.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa"));
    }

    private Operador operadorDaEmpresa(UUID clienteId, UUID operadorId) {
        return operadores.buscarPorId(operadorId).filter(o -> o.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Operador"));
    }
}
