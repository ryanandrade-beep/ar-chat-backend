package br.com.archat.aplicacao.operador;

import br.com.archat.aplicacao.porta.CodificadorSenha;
import br.com.archat.aplicacao.porta.ContaDeAcessoRepositorio;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.cliente.ContaDeAcesso;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Operadores da workspace, gerenciados pelo dono: cadastrar (com login próprio), alterar nome, ativar ou
 * desativar e redefinir a senha. O e-mail de login é único na plataforma (não pode repetir o de outro
 * operador nem o de um dono de workspace).
 */
public class GerenciarOperadoresUseCase {

    private final OperadorRepositorio operadores;
    private final ContaDeAcessoRepositorio contas;
    private final CodificadorSenha codificador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public GerenciarOperadoresUseCase(OperadorRepositorio operadores, ContaDeAcessoRepositorio contas,
                                      CodificadorSenha codificador, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.operadores = operadores;
        this.contas = contas;
        this.codificador = codificador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public List<Operador> listar(UUID clienteId) {
        return operadores.listarPorCliente(clienteId).stream()
                .sorted(Comparator.comparing(Operador::nome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Operador buscar(UUID clienteId, UUID operadorId) {
        return operadores.buscarPorId(operadorId).filter(o -> o.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Operador"));
    }

    public Operador cadastrar(UUID clienteId, String nome, String email, String senha) {
        ContaDeAcesso.validarSenha(senha);
        String normalizado = ContaDeAcesso.normalizarEmail(email);
        Operador novo = Operador.novo(clienteId, nome, normalizado, codificador.codificar(senha), relogio.instant());
        return unidadeDeTrabalho.executar(() -> {
            if (operadores.existeComEmail(normalizado) || contas.existeComEmail(normalizado)) {
                throw new RegraDeNegocioException("Este e-mail já é usado por outro login");
            }
            operadores.salvar(novo);
            return novo;
        });
    }

    public Operador alterar(UUID clienteId, UUID operadorId, String nome, boolean ativo) {
        return unidadeDeTrabalho.executar(() -> {
            Operador operador = buscar(clienteId, operadorId);
            operador.alterar(nome, ativo);
            operadores.salvar(operador);
            return operador;
        });
    }

    public void redefinirSenha(UUID clienteId, UUID operadorId, String novaSenha) {
        ContaDeAcesso.validarSenha(novaSenha);
        String hash = codificador.codificar(novaSenha);
        unidadeDeTrabalho.executar(() -> {
            Operador operador = buscar(clienteId, operadorId);
            operador.trocarSenha(hash);
            operadores.salvar(operador);
        });
    }
}
