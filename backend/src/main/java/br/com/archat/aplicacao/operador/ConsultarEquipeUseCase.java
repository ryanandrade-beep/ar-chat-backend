package br.com.archat.aplicacao.operador;

import br.com.archat.aplicacao.porta.ContaDeAcessoRepositorio;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.dominio.cliente.ContaDeAcesso;
import br.com.archat.dominio.cliente.Operador;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Backoffice: quem é o responsável (conta de acesso) e quais são os operadores de uma workspace. */
public class ConsultarEquipeUseCase {

    private final ContaDeAcessoRepositorio contas;
    private final OperadorRepositorio operadores;

    public ConsultarEquipeUseCase(ContaDeAcessoRepositorio contas, OperadorRepositorio operadores) {
        this.contas = contas;
        this.operadores = operadores;
    }

    public Equipe consultar(UUID clienteId) {
        return new Equipe(contas.buscarPorCliente(clienteId), operadores.listarPorCliente(clienteId));
    }

    public record Equipe(Optional<ContaDeAcesso> responsavel, List<Operador> operadores) {
    }
}
