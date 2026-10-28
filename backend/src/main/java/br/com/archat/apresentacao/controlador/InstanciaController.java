package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.whatsapp.GerenciarConexaoWhatsAppUseCase;
import br.com.archat.aplicacao.whatsapp.VincularInstanciaUseCase;
import br.com.archat.apresentacao.seguranca.AdministradorAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

/** Backoffice: qual instância da Z-API é o WhatsApp de cada empresa. Quem conecta o número é a empresa. */
@RestController
@Tag(name = "Instâncias WhatsApp (backoffice)")
class InstanciaController {

    private final VincularInstanciaUseCase instancias;
    private final GerenciarConexaoWhatsAppUseCase conexao;

    InstanciaController(VincularInstanciaUseCase instancias, GerenciarConexaoWhatsAppUseCase conexao) {
        this.instancias = instancias;
        this.conexao = conexao;
    }

    @GetMapping("/clients/{id}/whatsapp-instance")
    @Operation(summary = "Instância vinculada à empresa e se o número está conectado (admin)")
    InstanceResponse consultar(AdministradorAutenticado administrador, @PathVariable UUID id) {
        return resposta(id);
    }

    @PutMapping("/clients/{id}/whatsapp-instance")
    @Operation(summary = "Vincula à empresa uma instância da Z-API (ID e token); a empresa conecta pelo QR Code (admin)")
    InstanceResponse vincular(AdministradorAutenticado administrador, @PathVariable UUID id,
                              @Valid @RequestBody InstanceRequest r) {
        instancias.vincular(id, r.instanceId(), r.token());
        return resposta(id);
    }

    @DeleteMapping("/clients/{id}/whatsapp-instance")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Desvincula a instância; a empresa volta a usar a instância padrão da plataforma (admin)")
    void desvincular(AdministradorAutenticado administrador, @PathVariable UUID id) {
        instancias.desvincular(id);
    }

    private InstanceResponse resposta(UUID clienteId) {
        Boolean conectado;
        try {
            conectado = conexao.situacao(clienteId).conectado();
        } catch (RuntimeException e) {
            conectado = null;
        }
        Boolean situacao = conectado;
        return instancias.daEmpresa(clienteId)
                .map(i -> new InstanceResponse(true, i.instancia(), i.tokenMascarado(), i.vinculadaEm(), situacao))
                .orElseGet(() -> new InstanceResponse(false, null, null, null, situacao));
    }

    record InstanceRequest(@NotBlank String instanceId, @NotBlank String token) {
    }

    record InstanceResponse(boolean dedicated, String instanceId, String tokenMasked, Instant linkedAt, Boolean connected) {
    }
}
