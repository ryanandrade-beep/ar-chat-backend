package br.com.archat.apresentacao.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class AutenticacaoDtos {

    private AutenticacaoDtos() {
    }

    public record ClientAuthRequest(@NotBlank String documentId, @NotBlank String documentType) {
    }

    public record ClientAuthResponse(String token, ClienteDtos.ClientResponse client, String role, String userName,
                                     java.util.UUID operatorId) {

        public ClientAuthResponse(String token, ClienteDtos.ClientResponse client) {
            this(token, client, "owner", client.name(), null);
        }
    }

    public record EmailLoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record RegisterRequest(@NotBlank @Size(max = 120) String name, @NotBlank String email,
                                  @NotBlank String phone, @NotBlank String documentId, @NotBlank String documentType,
                                  @NotBlank String password, @NotBlank String passwordConfirmation,
                                  boolean acceptTerms) {
    }

    public record ForgotPasswordRequest(@NotBlank String email, String channel) {
    }

    public record ResetPasswordRequest(@NotBlank String email, @NotBlank String code, @NotBlank String password,
                                       @NotBlank String passwordConfirmation) {
    }

    public record AdminAuthRequest(@NotBlank String login, @NotBlank String password) {
    }

    public record AdminAuthResponse(String token) {
    }
}
