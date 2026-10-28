package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.autenticacao.AutenticarAdministradorUseCase;
import br.com.archat.aplicacao.autenticacao.AutenticarClienteUseCase;
import br.com.archat.aplicacao.conta.CadastrarContaUseCase;
import br.com.archat.aplicacao.conta.RecuperarSenhaUseCase;
import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.apresentacao.dto.ApiEnums;
import br.com.archat.apresentacao.dto.AutenticacaoDtos.AdminAuthRequest;
import br.com.archat.apresentacao.dto.AutenticacaoDtos.AdminAuthResponse;
import br.com.archat.apresentacao.dto.AutenticacaoDtos.ClientAuthRequest;
import br.com.archat.apresentacao.dto.AutenticacaoDtos.ClientAuthResponse;
import br.com.archat.apresentacao.dto.AutenticacaoDtos.EmailLoginRequest;
import br.com.archat.apresentacao.dto.AutenticacaoDtos.ForgotPasswordRequest;
import br.com.archat.apresentacao.dto.AutenticacaoDtos.RegisterRequest;
import br.com.archat.apresentacao.dto.AutenticacaoDtos.ResetPasswordRequest;
import br.com.archat.apresentacao.dto.ClienteDtos.ClientResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Autenticação")
class AutenticacaoController {

    private final AutenticarClienteUseCase autenticarCliente;
    private final AutenticarAdministradorUseCase autenticarAdministrador;
    private final CadastrarContaUseCase cadastrarConta;
    private final RecuperarSenhaUseCase recuperarSenha;
    private final GerenciarOperadoresUseCase equipe;

    AutenticacaoController(AutenticarClienteUseCase autenticarCliente,
                           AutenticarAdministradorUseCase autenticarAdministrador,
                           CadastrarContaUseCase cadastrarConta, RecuperarSenhaUseCase recuperarSenha,
                           GerenciarOperadoresUseCase equipe) {
        this.equipe = equipe;
        this.autenticarCliente = autenticarCliente;
        this.autenticarAdministrador = autenticarAdministrador;
        this.cadastrarConta = cadastrarConta;
        this.recuperarSenha = recuperarSenha;
    }

    @PostMapping("/auth/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cria a conta da empresa (plano pré-pago, sem saldo) e já devolve o token")
    ClientAuthResponse cadastrar(@Valid @RequestBody RegisterRequest r) {
        var autenticado = cadastrarConta.executar(new CadastrarContaUseCase.Comando(r.name(), r.email(), r.phone(),
                r.documentId(), ApiEnums.documento(r.documentType()), r.password(), r.passwordConfirmation(),
                r.acceptTerms()));
        return doDono(autenticado.token(), autenticado.cliente());
    }

    @PostMapping("/auth/login")
    @Operation(summary = "Autentica por e-mail e senha o dono da workspace ou um operador")
    ClientAuthResponse entrar(@Valid @RequestBody EmailLoginRequest r) {
        var autenticado = autenticarCliente.porEmail(r.email(), r.password());
        var operador = autenticado.operador();
        if (operador == null) {
            return doDono(autenticado.token(), autenticado.cliente());
        }
        return new ClientAuthResponse(autenticado.token(), ClientResponse.de(autenticado.cliente()), "operator",
                operador.nome(), operador.id());
    }

    @PostMapping("/auth/forgot-password")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Envia o código de redefinição por e-mail ou WhatsApp (responde igual se o e-mail não existir)")
    void esqueciSenha(@Valid @RequestBody ForgotPasswordRequest r) {
        recuperarSenha.solicitar(r.email(), "whatsapp".equalsIgnoreCase(r.channel())
                ? RecuperarSenhaUseCase.Canal.WHATSAPP : RecuperarSenhaUseCase.Canal.EMAIL);
    }

    @PostMapping("/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Troca a senha com o código recebido por e-mail (válido por 15 minutos, 5 tentativas)")
    void redefinirSenha(@Valid @RequestBody ResetPasswordRequest r) {
        recuperarSenha.redefinir(r.email(), r.code(), r.password(), r.passwordConfirmation());
    }

    @PostMapping("/auth")
    @Operation(summary = "Autentica o cliente pelo CPF/CNPJ (acesso de demonstração e da collection do Postman)")
    ClientAuthResponse autenticarCliente(@Valid @RequestBody ClientAuthRequest requisicao) {
        var autenticado = autenticarCliente.executar(requisicao.documentId(), ApiEnums.documento(requisicao.documentType()));
        return doDono(autenticado.token(), autenticado.cliente());
    }

    private ClientAuthResponse doDono(String token, br.com.archat.dominio.cliente.Cliente cliente) {
        return new ClientAuthResponse(token, ClientResponse.de(cliente), "owner", cliente.nome(),
                equipe.perfilDoDono(cliente.id(), cliente.nome()).id());
    }

    @PostMapping("/admin/auth")
    @Operation(summary = "Autentica o administrador por login e senha")
    AdminAuthResponse autenticarAdministrador(@Valid @RequestBody AdminAuthRequest requisicao) {
        return new AdminAuthResponse(autenticarAdministrador.executar(requisicao.login(), requisicao.password()));
    }
}
