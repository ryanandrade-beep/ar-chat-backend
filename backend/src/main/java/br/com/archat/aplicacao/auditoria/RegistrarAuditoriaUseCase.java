package br.com.archat.aplicacao.auditoria;

import br.com.archat.aplicacao.porta.AuditoriaRepositorio;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DepartamentoRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.EntregaRepositorio;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.dominio.auditoria.AcaoDeAuditoria;
import br.com.archat.dominio.auditoria.RegistroDeAuditoria;
import br.com.archat.dominio.cliente.Operador;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;

/**
 * Grava na trilha de auditoria. Nunca lança exceção: se não der para registrar, a ação do usuário continua valendo.
 */
public class RegistrarAuditoriaUseCase {
    public enum TipoDeRecurso { CONVERSA, ENTREGA, OPERADOR, DEPARTAMENTO }

    private final AuditoriaRepositorio auditoria;
    private final OperadorRepositorio operadores;
    private final ClienteRepositorio clientes;
    private final ConversaRepositorio conversas;
    private final DestinatarioRepositorio destinatarios;
    private final EntregaRepositorio entregas;
    private final DepartamentoRepositorio departamentos;
    private final Clock relogio;

    public RegistrarAuditoriaUseCase(AuditoriaRepositorio auditoria, OperadorRepositorio operadores, ClienteRepositorio clientes,
                                     ConversaRepositorio conversas, DestinatarioRepositorio destinatarios,
                                     EntregaRepositorio entregas, DepartamentoRepositorio departamentos, Clock relogio) {
        this.auditoria = auditoria;
        this.operadores = operadores;
        this.clientes = clientes;
        this.conversas = conversas;
        this.destinatarios = destinatarios;
        this.entregas = entregas;
        this.departamentos = departamentos;
        this.relogio = relogio;
    }

    public void registrar(UUID clienteId, UUID autorId, AcaoDeAuditoria acao, String recurso, String detalhe) {
        try {
            auditoria.salvar(RegistroDeAuditoria.novo(clienteId, autorId, nomeDoAutor(clienteId, autorId), acao, recurso,
                    detalhe, relogio.instant()));
        } catch (RuntimeException e) {
        }
    }

    public void registrarSobre(UUID clienteId, UUID autorId, AcaoDeAuditoria acao, TipoDeRecurso tipo, UUID recursoId,
                               String recursoInformado, String detalhe) {
        String recurso = recursoInformado != null ? recursoInformado : rotulo(clienteId, tipo, recursoId).orElse(null);
        registrar(clienteId, autorId, acao, recurso, detalhe);
    }

    private Optional<String> rotulo(UUID clienteId, TipoDeRecurso tipo, UUID id) {
        if (tipo == null || id == null) {
            return Optional.empty();
        }
        try {
            return switch (tipo) {
                case CONVERSA -> conversas.buscarPorId(id).filter(c -> c.pertenceA(clienteId))
                        .flatMap(c -> destinatarios.buscarPorId(c.destinatarioId()))
                        .map(d -> "Atendimento de " + d.nome());
                case ENTREGA -> entregas.buscarPorId(id).filter(e -> e.clienteId().equals(clienteId))
                        .map(e -> "Entrega de " + e.nomeCliente() + " · " + e.endereco());
                case OPERADOR -> operadores.buscarPorId(id).filter(o -> o.pertenceA(clienteId))
                        .map(o -> "Operador " + o.nome());
                case DEPARTAMENTO -> departamentos.buscarPorId(id).filter(d -> d.clienteId().equals(clienteId))
                        .map(d -> "Departamento " + d.nome());
            };
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private String nomeDoAutor(UUID clienteId, UUID autorId) {
        if (autorId != null) {
            Optional<String> nome = operadores.buscarPorId(autorId).map(Operador::nome);
            if (nome.isPresent()) {
                return nome.get();
            }
        }
        return clientes.buscarPorId(clienteId).map(c -> c.nome()).orElse("Responsável");
    }
}
