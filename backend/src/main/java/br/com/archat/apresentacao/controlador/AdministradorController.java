package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.administrador.GerenciarAdministradoresUseCase;
import br.com.archat.apresentacao.dto.AdministradorDtos.AdminResponse;
import br.com.archat.apresentacao.dto.AdministradorDtos.ChangePasswordRequest;
import br.com.archat.apresentacao.dto.AdministradorDtos.CreateAdminRequest;
import br.com.archat.apresentacao.dto.AdministradorDtos.UpdateMyAccountRequest;
import br.com.archat.apresentacao.seguranca.AdministradorAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/admins")
@Tag(name = "Administradores")
class AdministradorController {

    private final GerenciarAdministradoresUseCase administradores;

    AdministradorController(GerenciarAdministradoresUseCase administradores) {
        this.administradores = administradores;
    }

    @GetMapping
    @Operation(summary = "Lista os administradores")
    List<AdminResponse> listar(AdministradorAutenticado administrador) {
        return administradores.listar().stream().map(AdminResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um administrador")
    AdminResponse cadastrar(AdministradorAutenticado administrador, @Valid @RequestBody CreateAdminRequest r) {
        return AdminResponse.de(administradores.cadastrar(r.name(), r.login(), r.password()));
    }

    @GetMapping("/me")
    @Operation(summary = "Minha conta: dados do administrador autenticado")
    AdminResponse minhaConta(AdministradorAutenticado administrador) {
        return AdminResponse.de(administradores.buscar(administrador.id()));
    }

    @PutMapping("/me")
    @Operation(summary = "Minha conta: altera nome, e-mail e telefone (o login não muda)")
    AdminResponse alterarMinhaConta(AdministradorAutenticado administrador, @Valid @RequestBody UpdateMyAccountRequest r) {
        return AdminResponse.de(administradores.atualizarDados(administrador.id(), r.name(), r.email(), r.phone()));
    }

    @PutMapping("/me/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Minha conta: troca a senha informando a atual")
    void trocarSenha(AdministradorAutenticado administrador, @Valid @RequestBody ChangePasswordRequest r) {
        administradores.trocarSenha(administrador.id(), r.currentPassword(), r.newPassword(), r.passwordConfirmation());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Remove um administrador (não pode remover a si mesmo)")
    void excluir(AdministradorAutenticado administrador, @PathVariable UUID id) {
        administradores.excluir(administrador.id(), id);
    }
}
