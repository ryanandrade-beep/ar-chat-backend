package br.com.archat.aplicacao.autenticacao;

import br.com.archat.aplicacao.excecao.AcessoNegadoException;
import br.com.archat.aplicacao.excecao.NaoAutenticadoException;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.CodificadorSenha;
import br.com.archat.aplicacao.porta.ContaDeAcessoRepositorio;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.aplicacao.porta.ServicoToken;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.ContaDeAcesso;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.dominio.cliente.Documento;
import br.com.archat.dominio.cliente.TipoDocumento;

import java.time.Clock;
import java.time.Duration;

public class AutenticarClienteUseCase {

    private final ClienteRepositorio clientes;
    private final ServicoToken tokens;
    private final Clock relogio;
    private final Duration validadeToken;
    private final ContaDeAcessoRepositorio contas;
    private final CodificadorSenha codificador;
    private final OperadorRepositorio operadores;

    public AutenticarClienteUseCase(ClienteRepositorio clientes, ServicoToken tokens, Clock relogio,
                                    Duration validadeToken) {
        this(clientes, tokens, relogio, validadeToken, null, null, null);
    }

    public AutenticarClienteUseCase(ClienteRepositorio clientes, ServicoToken tokens, Clock relogio,
                                    Duration validadeToken, ContaDeAcessoRepositorio contas,
                                    CodificadorSenha codificador, OperadorRepositorio operadores) {
        this.clientes = clientes;
        this.tokens = tokens;
        this.relogio = relogio;
        this.validadeToken = validadeToken;
        this.contas = contas;
        this.codificador = codificador;
        this.operadores = operadores;
    }

    public ClienteAutenticado porEmail(String email, String senha) {
        NaoAutenticadoException invalido = new NaoAutenticadoException("E-mail ou senha inválidos");
        String normalizado;
        try {
            normalizado = ContaDeAcesso.normalizarEmail(email);
        } catch (RuntimeException e) {
            throw invalido;
        }
        var conta = contas.buscarPorEmail(normalizado);
        if (conta.isPresent()) {
            if (senha == null || !codificador.confere(senha, conta.get().senhaHash())) {
                throw invalido;
            }
            return abrirSessao(clientes.buscarPorId(conta.get().clienteId()).orElseThrow(() -> invalido));
        }
        Operador operador = (operadores == null ? java.util.Optional.<Operador>empty() : operadores.buscarPorEmail(normalizado))
                .orElseThrow(() -> invalido);
        if (senha == null || !codificador.confere(senha, operador.senhaHash())) {
            throw invalido;
        }
        if (!operador.ativo()) {
            throw new AcessoNegadoException("Operador desativado; fale com o responsável pela conta");
        }
        Cliente cliente = clientes.buscarPorId(operador.clienteId()).orElseThrow(() -> invalido);
        if (!cliente.ativo()) {
            throw new AcessoNegadoException("Cliente inativo");
        }
        Sessao sessao = new Sessao(Sessao.Perfil.OPERADOR, operador.id(), relogio.instant().plus(validadeToken));
        return new ClienteAutenticado(tokens.gerar(sessao), cliente, operador);
    }

    public ClienteAutenticado executar(String numeroDocumento, TipoDocumento tipoDocumento) {
        Cliente cliente = clientes.buscarPorDocumento(new Documento(numeroDocumento, tipoDocumento))
                .orElseThrow(() -> new NaoAutenticadoException("Cliente não cadastrado"));
        return abrirSessao(cliente);
    }

    private ClienteAutenticado abrirSessao(Cliente cliente) {
        if (!cliente.ativo()) {
            throw new AcessoNegadoException("Cliente inativo");
        }
        Sessao sessao = new Sessao(Sessao.Perfil.CLIENTE, cliente.id(), relogio.instant().plus(validadeToken));
        return new ClienteAutenticado(tokens.gerar(sessao), cliente);
    }

    public record ClienteAutenticado(String token, Cliente cliente, Operador operador) {

        public ClienteAutenticado(String token, Cliente cliente) {
            this(token, cliente, null);
        }
    }
}
