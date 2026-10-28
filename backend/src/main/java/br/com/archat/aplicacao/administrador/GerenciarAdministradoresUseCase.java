package br.com.archat.aplicacao.administrador;

import br.com.archat.aplicacao.porta.Administrador;
import br.com.archat.aplicacao.porta.AdministradorRepositorio;
import br.com.archat.aplicacao.porta.CodificadorSenha;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.util.List;
import java.util.UUID;

public class GerenciarAdministradoresUseCase {

    private static final int TAMANHO_MINIMO_SENHA = 8;

    private final AdministradorRepositorio administradores;
    private final CodificadorSenha codificador;
    private final UnidadeDeTrabalho unidadeDeTrabalho;

    public GerenciarAdministradoresUseCase(AdministradorRepositorio administradores, CodificadorSenha codificador,
                                           UnidadeDeTrabalho unidadeDeTrabalho) {
        this.administradores = administradores;
        this.codificador = codificador;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
    }

    public List<Administrador> listar() {
        return administradores.listarTodos();
    }

    public Administrador cadastrar(String nome, String login, String senha) {
        if (senha == null || senha.length() < TAMANHO_MINIMO_SENHA) {
            throw new RegraDeNegocioException("A senha deve ter no mínimo " + TAMANHO_MINIMO_SENHA + " caracteres");
        }
        Administrador novo = new Administrador(UUID.randomUUID(), nome, login, codificador.codificar(senha));
        return unidadeDeTrabalho.executar(() -> {
            if (administradores.buscarPorLogin(novo.login()).isPresent()) {
                throw new RegraDeNegocioException("Já existe um administrador com este login");
            }
            administradores.salvar(novo);
            return novo;
        });
    }

    public Administrador buscar(UUID administradorId) {
        return administradores.buscarPorId(administradorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Administrador"));
    }

    public Administrador atualizarDados(UUID administradorId, String nome, String email, String telefone) {
        return unidadeDeTrabalho.executar(() -> {
            Administrador atual = buscar(administradorId);
            Administrador alterado = new Administrador(atual.id(), nome, atual.login(), atual.senhaHash(), email, telefone);
            if (alterado.email() != null && administradores.listarTodos().stream()
                    .anyMatch(a -> !a.id().equals(atual.id()) && alterado.email().equals(a.email()))) {
                throw new RegraDeNegocioException("Já existe um administrador com este e-mail");
            }
            administradores.salvar(alterado);
            return alterado;
        });
    }

    public void trocarSenha(UUID administradorId, String senhaAtual, String novaSenha, String confirmacao) {
        if (novaSenha == null || novaSenha.length() < TAMANHO_MINIMO_SENHA || novaSenha.length() > 72) {
            throw new RegraDeNegocioException("A senha deve ter no mínimo " + TAMANHO_MINIMO_SENHA + " caracteres");
        }
        if (!novaSenha.equals(confirmacao)) {
            throw new RegraDeNegocioException("A confirmação não confere com a senha");
        }
        unidadeDeTrabalho.executar(() -> {
            Administrador atual = buscar(administradorId);
            if (senhaAtual == null || !codificador.confere(senhaAtual, atual.senhaHash())) {
                throw new RegraDeNegocioException("A senha atual está incorreta");
            }
            if (codificador.confere(novaSenha, atual.senhaHash())) {
                throw new RegraDeNegocioException("A nova senha deve ser diferente da atual");
            }
            administradores.salvar(new Administrador(atual.id(), atual.nome(), atual.login(),
                    codificador.codificar(novaSenha), atual.email(), atual.telefone()));
        });
    }

    public void excluir(UUID solicitanteId, UUID administradorId) {
        if (solicitanteId.equals(administradorId)) {
            throw new RegraDeNegocioException("O administrador não pode excluir a si mesmo");
        }
        unidadeDeTrabalho.executar(() -> {
            administradores.buscarPorId(administradorId)
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Administrador"));
            administradores.excluir(administradorId);
        });
    }

    public boolean criarInicialSeNecessario(String nome, String login, String senha) {
        if (administradores.contar() > 0) {
            return false;
        }
        cadastrar(nome, login, senha);
        return true;
    }
}
