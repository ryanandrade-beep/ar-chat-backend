package br.com.archat.aplicacao.conta;

import br.com.archat.aplicacao.porta.CodificadorSenha;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.aplicacao.porta.ContaDeAcessoRepositorio;
import br.com.archat.aplicacao.porta.EnvioDeEmail;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.cliente.ContaDeAcesso;
import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.Optional;

/**
 * "Esqueci minha senha": envia por e-mail ou WhatsApp um código de 6 dígitos (válido por 15 minutos) e troca a senha
 * quando o código confere. A solicitação responde igual para e-mails cadastrados ou não, para não revelar
 * quem tem conta.
 */
public class RecuperarSenhaUseCase {

    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final ContaDeAcessoRepositorio contas;
    private final CodificadorSenha codificador;
    private final EnvioDeEmail email;
    private final ConexaoWhatsApp whatsapp;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;

    public RecuperarSenhaUseCase(ContaDeAcessoRepositorio contas, CodificadorSenha codificador, EnvioDeEmail email,
                                 UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this(contas, codificador, email, null, unidadeDeTrabalho, relogio);
    }

    public RecuperarSenhaUseCase(ContaDeAcessoRepositorio contas, CodificadorSenha codificador, EnvioDeEmail email,
                                 ConexaoWhatsApp whatsapp, UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this.contas = contas;
        this.codificador = codificador;
        this.email = email;
        this.whatsapp = whatsapp;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public void solicitar(String enderecoEmail) {
        solicitar(enderecoEmail, Canal.EMAIL);
    }

    public void solicitar(String enderecoEmail, Canal canal) {
        String normalizado;
        try {
            normalizado = ContaDeAcesso.normalizarEmail(enderecoEmail);
        } catch (RegraDeNegocioException e) {
            return;
        }
        String codigo = "%06d".formatted(ALEATORIO.nextInt(1_000_000));
        Optional<ContaDeAcesso> conta = unidadeDeTrabalho.executar(() -> contas.buscarPorEmail(normalizado).map(c -> {
            c.registrarCodigoDeRecuperacao(codificador.codificar(codigo), relogio.instant());
            contas.salvar(c);
            return c;
        }));
        conta.ifPresent(c -> enviarCodigo(c, codigo, canal));
    }

    private void enviarCodigo(ContaDeAcesso conta, String codigo, Canal canal) {
        String texto = "A&R Chat: seu código para redefinir a senha é " + codigo
                + ". Ele vale por 15 minutos. Se não foi você, ignore esta mensagem.";
        if (canal == Canal.WHATSAPP && whatsapp != null) {
            try {
                whatsapp.enviarAvisoDoSistema(null, conta.telefone(), texto);
                return;
            } catch (RuntimeException e) {
            }
        }
        email.enviar(conta.email(), "A&R Chat – código para redefinir a senha", texto);
    }

    public enum Canal { EMAIL, WHATSAPP }

    public void redefinir(String enderecoEmail, String codigo, String novaSenha, String confirmacao) {
        ContaDeAcesso.validarSenha(novaSenha);
        if (!novaSenha.equals(confirmacao)) {
            throw new RegraDeNegocioException("A confirmação não confere com a senha");
        }
        String novaSenhaHash = codificador.codificar(novaSenha);
        String normalizado = ContaDeAcesso.normalizarEmail(enderecoEmail);
        RegraDeNegocioException erro = unidadeDeTrabalho.executar(() -> {
            ContaDeAcesso conta = contas.buscarPorEmail(normalizado)
                    .orElseThrow(() -> new RegraDeNegocioException("Código expirado ou inválido; solicite um novo"));
            boolean confere = conta.codigoHash() != null && codigo != null
                    && codificador.confere(codigo.strip(), conta.codigoHash());
            try {
                conta.redefinirSenha(confere, novaSenhaHash, relogio.instant());
                contas.salvar(conta);
                return null;
            } catch (RegraDeNegocioException e) {
                contas.salvar(conta);
                return e;
            }
        });
        if (erro != null) {
            throw erro;
        }
    }
}
