package br.com.archat.apresentacao.dto;

import br.com.archat.aplicacao.porta.Administrador;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class AdministradorDtos {

    private AdministradorDtos() {
    }

    public record CreateAdminRequest(@NotBlank @Size(max = 120) String name, @NotBlank String login,
                                     @NotBlank @Size(min = 8, max = 72) String password) {
    }

    public record UpdateMyAccountRequest(@NotBlank @Size(max = 120) String name, String email, String phone) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword,
                                        @NotBlank String passwordConfirmation) {
    }

    public record AdminResponse(UUID id, String name, String login, String email, String phone) {

        public static AdminResponse de(Administrador a) {
            return new AdminResponse(a.id(), a.nome(), a.login(), a.email(), a.telefone());
        }
    }
}
