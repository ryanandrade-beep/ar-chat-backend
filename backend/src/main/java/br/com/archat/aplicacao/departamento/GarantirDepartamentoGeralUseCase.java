package br.com.archat.aplicacao.departamento;

import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DepartamentoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.conversa.Conversa;

import java.util.List;

/**
 * Ao iniciar o sistema, garante que toda workspace tenha o departamento Geral e que as conversas que estavam
 * sem departamento passem a ficar nele. Pode rodar quantas vezes for preciso: só mexe no que falta.
 */
public class GarantirDepartamentoGeralUseCase {
    private final ClienteRepositorio clientes;
    private final DepartamentoRepositorio departamentos;
    private final ConversaRepositorio conversas;
    private final UnidadeDeTrabalho unidadeDeTrabalho;

    public GarantirDepartamentoGeralUseCase(ClienteRepositorio clientes, DepartamentoRepositorio departamentos,
                                            ConversaRepositorio conversas, UnidadeDeTrabalho unidadeDeTrabalho) {
        this.clientes = clientes;
        this.departamentos = departamentos;
        this.conversas = conversas;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
    }

    public int executar() {
        int movidas = 0;
        for (Cliente cliente : clientes.listarTodos()) {
            movidas += unidadeDeTrabalho.executar(() -> {
                departamentos.garantirGeral(cliente.id());
                List<Conversa> semDepartamento = conversas.listarPorCliente(cliente.id()).stream()
                        .filter(c -> c.departamentoId() == null).toList();
                semDepartamento.forEach(conversas::salvar);
                return semDepartamento.size();
            });
        }
        return movidas;
    }
}
