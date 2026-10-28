package br.com.archat.aplicacao;

import br.com.archat.aplicacao.administrador.GerenciarAdministradoresUseCase;
import br.com.archat.aplicacao.conta.RecuperarSenhaUseCase;
import br.com.archat.aplicacao.porta.AdministradorRepositorio;
import br.com.archat.aplicacao.porta.Administrador;
import br.com.archat.aplicacao.porta.CodificadorSenha;
import br.com.archat.aplicacao.porta.ContaDeAcessoRepositorio;
import br.com.archat.dominio.cliente.ContaDeAcesso;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContaUseCasesTest {

    private final Clock relogio = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);

    static final CodificadorSenha CODIFICADOR = new CodificadorSenha() {
        public String codificar(String senha) { return "h:" + senha; }
        public boolean confere(String senha, String hash) { return ("h:" + senha).equals(hash); }
    };

    static class Contas implements ContaDeAcessoRepositorio {
        final Map<String, ContaDeAcesso> porEmail = new HashMap<>();
        public void salvar(ContaDeAcesso c) { porEmail.put(c.email(), c); }
        public Optional<ContaDeAcesso> buscarPorEmail(String email) { return Optional.ofNullable(porEmail.get(email)); }
        public Optional<ContaDeAcesso> buscarPorCliente(UUID id) {
            return porEmail.values().stream().filter(c -> c.clienteId().equals(id)).findFirst();
        }
        public boolean existeComEmail(String email) { return porEmail.containsKey(email); }
    }

    static class Admins implements AdministradorRepositorio {
        final Map<UUID, Administrador> dados = new HashMap<>();
        public void salvar(Administrador a) { dados.put(a.id(), a); }
        public Optional<Administrador> buscarPorId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public Optional<Administrador> buscarPorLogin(String login) {
            return dados.values().stream().filter(a -> a.login().equals(login)).findFirst();
        }
        public List<Administrador> listarTodos() { return new ArrayList<>(dados.values()); }
        public void excluir(UUID id) { dados.remove(id); }
        public long contar() { return dados.size(); }
    }

    @Test
    void codigoPeloWhatsAppVaiParaOTelefoneDoCadastroEPorEmailSeOWhatsAppFalhar() {
        Contas contas = new Contas();
        contas.salvar(ContaDeAcesso.nova(UUID.randomUUID(), "ze@acougue.com", "44988887777", "h:x", true, "v", relogio.instant()));
        List<String> emails = new ArrayList<>();
        ConexaoWhatsAppUseCasesTest.CanalFalso whatsapp = new ConexaoWhatsAppUseCasesTest.CanalFalso();
        RecuperarSenhaUseCase recuperar = new RecuperarSenhaUseCase(contas, CODIFICADOR, (para, assunto, texto) -> emails.add(para),
                whatsapp, Fakes.SEM_TRANSACAO, relogio);

        recuperar.solicitar("ze@acougue.com", RecuperarSenhaUseCase.Canal.WHATSAPP);
        assertThat(whatsapp.avisos).singleElement().asString().startsWith("44988887777:").containsPattern("\\d{6}");
        assertThat(emails).isEmpty();

        whatsapp.falhar = true;
        recuperar.solicitar("ze@acougue.com", RecuperarSenhaUseCase.Canal.WHATSAPP);
        assertThat(emails).containsExactly("ze@acougue.com");
    }

    @Test
    void administradorAlteraOsPropriosDadosETrocaASenhaConferindoAAtual() {
        Admins admins = new Admins();
        GerenciarAdministradoresUseCase gerenciar = new GerenciarAdministradoresUseCase(admins, CODIFICADOR, Fakes.SEM_TRANSACAO);
        Administrador ana = gerenciar.cadastrar("Ana", "ana", "senha-antiga");

        Administrador alterada = gerenciar.atualizarDados(ana.id(), "Ana Paula", "Ana@Mercado.com", "(44) 99999-0000");
        assertThat(alterada.login()).isEqualTo("ana");
        assertThat(alterada.email()).isEqualTo("ana@mercado.com");
        assertThat(alterada.telefone()).isEqualTo("44999990000");

        assertThatThrownBy(() -> gerenciar.trocarSenha(ana.id(), "errada", "nova-senha-1", "nova-senha-1"))
                .isInstanceOf(RegraDeNegocioException.class).hasMessageContaining("atual");
        assertThatThrownBy(() -> gerenciar.trocarSenha(ana.id(), "senha-antiga", "nova-senha-1", "outra"))
                .isInstanceOf(RegraDeNegocioException.class);
        gerenciar.trocarSenha(ana.id(), "senha-antiga", "nova-senha-1", "nova-senha-1");
        assertThat(admins.buscarPorId(ana.id()).orElseThrow().senhaHash()).isEqualTo("h:nova-senha-1");
        assertThat(admins.buscarPorId(ana.id()).orElseThrow().email()).isEqualTo("ana@mercado.com");
    }
}
