package br.com.archat.aplicacao.operador;

import br.com.archat.aplicacao.plano.PlanoDoCliente;
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
    private final PlanoDoCliente planos;

    public GerenciarOperadoresUseCase(OperadorRepositorio operadores, ContaDeAcessoRepositorio contas,
                                      CodificadorSenha codificador, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this(operadores, contas, codificador, unidadeDeTrabalho, relogio, null);
    }

    public GerenciarOperadoresUseCase(OperadorRepositorio operadores, ContaDeAcessoRepositorio contas,
                                      CodificadorSenha codificador, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio,
                                      PlanoDoCliente planos) {
        this.planos = planos;
        this.operadores = operadores;
        this.contas = contas;
        this.codificador = codificador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public Operador perfilDoDono(UUID clienteId, String nomeDoDono) {
        return operadores.listarPorCliente(clienteId).stream().filter(Operador::dono).findFirst()
                .orElseGet(() -> unidadeDeTrabalho.executar(() -> {
                    Operador perfil = Operador.perfilDoDono(clienteId, nomeDoDono, relogio.instant());
                    operadores.salvar(perfil);
                    return perfil;
                }));
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
            garantirVaga(clienteId);
            operadores.salvar(novo);
            return novo;
        });
    }

    public Operador alterar(UUID clienteId, UUID operadorId, String nome, boolean ativo) {
        return unidadeDeTrabalho.executar(() -> {
            Operador operador = buscar(clienteId, operadorId);
            if (ativo && !operador.ativo()) {
                garantirVaga(clienteId);
            }
            operador.alterar(nome, ativo);
            operadores.salvar(operador);
            return operador;
        });
    }

    public java.util.Optional<Integer> limite(UUID clienteId) {
        return planos == null ? java.util.Optional.empty() : planos.limiteDeOperadores(clienteId);
    }

    private void garantirVaga(UUID clienteId) {
        limite(clienteId).ifPresent(limite -> {
            long ativos = operadores.listarPorCliente(clienteId).stream().filter(o -> o.ativo() && !o.dono()).count();
            if (ativos >= limite) {
                throw new RegraDeNegocioException("Seu plano inclui %d operador(es). Para contratar mais, mude de plano em Assinatura."
                        .formatted(limite));
            }
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
