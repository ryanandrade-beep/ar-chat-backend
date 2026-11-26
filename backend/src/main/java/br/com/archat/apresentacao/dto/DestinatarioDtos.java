package br.com.archat.apresentacao.dto;

import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.conversa.TipoDestinatario;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public final class DestinatarioDtos {

    private DestinatarioDtos() {
    }

    public record RecipientRequest(String type, @NotBlank @Size(max = 120) String name,
                                   @NotBlank @Size(max = 40) String phone, Boolean acceptsMarketing,
                                   java.util.List<String> tags) {

        public boolean aceitaOfertas() {
            return Boolean.TRUE.equals(acceptsMarketing);
        }

        public TipoDestinatario tipo() {
            if (type == null || type.isBlank() || type.equalsIgnoreCase("contact")) {
                return TipoDestinatario.CONTATO;
            }
            if (type.equalsIgnoreCase("group")) {
                return TipoDestinatario.GRUPO;
            }
            throw new RegraDeNegocioException("type inválido: use contact ou group");
        }
    }

    public record RecipientResponse(UUID id, String type, String name, String phone, boolean acceptsMarketing,
                                    java.util.List<String> tags) {

        public static RecipientResponse de(Destinatario d) {
            return de(d, java.util.List.of());
        }

        public static RecipientResponse de(Destinatario d, java.util.List<String> etiquetas) {
            return new RecipientResponse(d.id(), d.grupo() ? "group" : "contact", d.nome(), d.telefone(),
                    d.aceitaOfertas(), etiquetas);
        }
    }
}
