package br.com.archat.aplicacao;

import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.aplicacao.porta.CodificadorSenha;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.dominio.cliente.Operador;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperadorEmailPorEmpresaTest {

    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);
    private final CodificadorSenha codificador = new CodificadorSenha() {
        public String codificar(String senha) { return "h:" + senha; }
        public boolean confere(String senha, String hash) { return ("h:" + senha).equals(hash); }
    };

    static class Operadores implements OperadorRepositorio {
        final List<Operador> dados = new ArrayList<>();
        public void salvar(Operador o) { dados.removeIf(x -> x.id().equals(o.id())); dados.add(o); }
        public Optional<Operador> buscarPorId(UUID id) { return dados.stream().filter(o -> o.id().equals(id)).findFirst(); }
        public List<Operador> listarPorEmail(String email) { return dados.stream().filter(o -> o.email().equals(email)).toList(); }
        public List<Operador> listarPorCliente(UUID clienteId) { return dados.stream().filter(o -> o.pertenceA(clienteId)).toList(); }
        public boolean existeComEmailNaEmpresa(UUID clienteId, String email) {
            return dados.stream().anyMatch(o -> o.pertenceA(clienteId) && o.email().equals(email));
        }
    }

    private final Operadores operadores = new Operadores();
    private final ContaUseCasesTest.Contas contas = new ContaUseCasesTest.Contas();
    private final GerenciarOperadoresUseCase casoDeUso =
            new GerenciarOperadoresUseCase(operadores, contas, codificador, Fakes.SEM_TRANSACAO, relogio);

    @Test
    void mesmoEmailPodeExistirEmEmpresasDiferentes() {
        UUID empresaA = UUID.randomUUID();
        UUID empresaB = UUID.randomUUID();
        casoDeUso.cadastrar(empresaA, "Ana", "ana@exemplo.com", "Senha1234");

        Operador naOutra = casoDeUso.cadastrar(empresaB, "Ana", "ana@exemplo.com", "Senha1234");

        assertThat(naOutra.email()).isEqualTo("ana@exemplo.com");
        assertThat(operadores.listarPorEmail("ana@exemplo.com")).hasSize(2);
    }

    @Test
    void emailRepetidoNaMesmaEmpresaEhRecusado() {
        UUID empresa = UUID.randomUUID();
        casoDeUso.cadastrar(empresa, "Ana", "ana@exemplo.com", "Senha1234");

        assertThatThrownBy(() -> casoDeUso.cadastrar(empresa, "Outra Ana", "ana@exemplo.com", "Senha1234"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("desta empresa");
    }

    @Test
    void editarParaEmailDeOutroOperadorDaMesmaEmpresaEhRecusadoMasDeOutraEmpresaNao() {
        UUID empresaA = UUID.randomUUID();
        UUID empresaB = UUID.randomUUID();
        casoDeUso.cadastrar(empresaB, "Beto", "beto@exemplo.com", "Senha1234");
        casoDeUso.cadastrar(empresaA, "Ana", "ana@exemplo.com", "Senha1234");
        Operador carla = casoDeUso.cadastrar(empresaA, "Carla", "carla@exemplo.com", "Senha1234");

        assertThatThrownBy(() -> casoDeUso.alterar(empresaA, carla.id(), "Carla", "ana@exemplo.com", true))
                .isInstanceOf(RegraDeNegocioException.class);
        Operador editada = casoDeUso.alterar(empresaA, carla.id(), "Carla", "beto@exemplo.com", true);

        assertThat(editada.email()).isEqualTo("beto@exemplo.com");
    }

    @Test
    void nomeEhLimpoEUnicoDentroDaEmpresa() {
        UUID empresaA = UUID.randomUUID();
        UUID empresaB = UUID.randomUUID();

        Operador ana = casoDeUso.cadastrar(empresaA, "  ana   maria  ", "ana@exemplo.com", "Senha1234");

        assertThat(ana.nome()).isEqualTo("ana maria");
        assertThatThrownBy(() -> casoDeUso.cadastrar(empresaA, "ANA MARIA", "outra@exemplo.com", "Senha1234"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("nome");
        assertThat(casoDeUso.cadastrar(empresaB, "Ana Maria", "ana@exemplo.com", "Senha1234").nome()).isEqualTo("Ana Maria");
        assertThatThrownBy(() -> casoDeUso.cadastrar(empresaA, "A", "a@exemplo.com", "Senha1234"))
                .isInstanceOf(RegraDeNegocioException.class);
        assertThat(casoDeUso.alterar(empresaA, ana.id(), "Ana  Maria", "ana@exemplo.com", true).nome()).isEqualTo("Ana Maria");
    }

    @Test
    void nomeComCaractereEspecialEEmailMalFormadoSaoRecusados() {
        UUID empresa = UUID.randomUUID();

        for (String nome : List.of("Ana@#$", "Ana$$", "<b>Ana</b>", "Ana;DROP", "@Ana", "1Ana")) {
            assertThatThrownBy(() -> casoDeUso.cadastrar(empresa, nome, "ana@exemplo.com", "Senha1234"))
                    .as(nome).isInstanceOf(RegraDeNegocioException.class);
        }
        for (String email : List.of("ryan@123", "ryan@123.1", "ryan@exemplo", "ryan@@exemplo.com", "ryan@.com",
                "ryan..x@exemplo.com", "ryan@exemplo.c", "ry an@exemplo.com", "ryan@-exemplo.com", "ryan@exemplo-.com")) {
            assertThatThrownBy(() -> casoDeUso.cadastrar(empresa, "Ana", email, "Senha1234"))
                    .as(email).isInstanceOf(RegraDeNegocioException.class);
        }
        assertThat(casoDeUso.cadastrar(empresa, "José d'Ávila-Souza Jr.", "jose.silva+qa@sub.exemplo.com.br", "Senha1234").nome())
                .isEqualTo("José d'Ávila-Souza Jr.");
    }
}
