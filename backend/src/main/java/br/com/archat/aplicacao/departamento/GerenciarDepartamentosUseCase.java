package br.com.archat.aplicacao.departamento;

import br.com.archat.aplicacao.porta.DepartamentoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.departamento.ConfiguracaoUra;
import br.com.archat.dominio.departamento.Departamento;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/** Departamentos da workspace e configuração da URA, gerenciados pelo dono. */
public class GerenciarDepartamentosUseCase {

    private final DepartamentoRepositorio departamentos;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public GerenciarDepartamentosUseCase(DepartamentoRepositorio departamentos, UnidadeDeTrabalho unidadeDeTrabalho,
                                         Clock relogio) {
        this.departamentos = departamentos;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public List<Departamento> listar(UUID clienteId) {
        return departamentos.listarPorCliente(clienteId).stream()
                .sorted(Comparator.comparing(Departamento::nome, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public Departamento buscar(UUID clienteId, UUID departamentoId) {
        return departamentos.buscarPorId(departamentoId).filter(d -> d.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Departamento"));
    }

    public Departamento criar(UUID clienteId, String nome) {
        Departamento novo = Departamento.novo(clienteId, nome, relogio.instant());
        return unidadeDeTrabalho.executar(() -> {
            if (departamentos.existeComNome(clienteId, novo.nome(), null)) {
                throw new RegraDeNegocioException("Já existe um departamento com este nome");
            }
            departamentos.salvar(novo);
            return novo;
        });
    }

    public Departamento alterar(UUID clienteId, UUID departamentoId, String nome, boolean ativo) {
        return unidadeDeTrabalho.executar(() -> {
            Departamento departamento = buscar(clienteId, departamentoId);
            departamento.alterar(nome, ativo);
            if (departamentos.existeComNome(clienteId, departamento.nome(), departamento.id())) {
                throw new RegraDeNegocioException("Já existe um departamento com este nome");
            }
            departamentos.salvar(departamento);
            return departamento;
        });
    }

    public ConfiguracaoUra configuracao(UUID clienteId) {
        return departamentos.buscarConfiguracao(clienteId).orElseGet(() -> ConfiguracaoUra.padrao(clienteId));
    }

    public ConfiguracaoUra configurar(UUID clienteId, boolean ativa, String boasVindas, String pergunta,
                                      int maximoTentativas, UUID departamentoPadraoId, boolean ignorarGrupos) {
        return unidadeDeTrabalho.executar(() -> {
            if (departamentoPadraoId != null) {
                buscar(clienteId, departamentoPadraoId);
            }
            if (ativa && listar(clienteId).stream().noneMatch(Departamento::ativo)) {
                throw new RegraDeNegocioException("Cadastre ao menos um departamento ativo antes de ligar a URA");
            }
            ConfiguracaoUra configuracao = configuracao(clienteId);
            configuracao.alterar(ativa, boasVindas, pergunta, maximoTentativas, departamentoPadraoId, ignorarGrupos);
            departamentos.salvar(configuracao);
            return configuracao;
        });
    }
}
