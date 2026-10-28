package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.destinatario.GerenciarDestinatariosUseCase;
import br.com.archat.aplicacao.destinatario.ImportarContatosUseCase;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.apresentacao.dto.DestinatarioDtos.RecipientRequest;
import br.com.archat.apresentacao.dto.DestinatarioDtos.RecipientResponse;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/recipients")
@Tag(name = "Destinatários", description = "Contatos e grupos do WhatsApp do cliente autenticado")
class DestinatarioController {

    private final GerenciarDestinatariosUseCase destinatarios;
    private final ImportarContatosUseCase importar;

    DestinatarioController(GerenciarDestinatariosUseCase destinatarios, ImportarContatosUseCase importar) {
        this.destinatarios = destinatarios;
        this.importar = importar;
    }

    @GetMapping("/whatsapp-contacts")
    @Operation(summary = "Contatos da agenda do WhatsApp conectado, indicando os já cadastrados")
    List<PhoneContactResponse> agenda(ClienteAutenticado cliente, @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "100") int size) {
        return importar.listar(cliente.id(), page, size).stream()
                .map(c -> new PhoneContactResponse(c.telefone(), c.nome(), c.jaCadastrado())).toList();
    }

    @PostMapping("/import")
    @Operation(summary = "Importa contatos da agenda como destinatários (sem consentimento para ofertas; duplicados são ignorados)")
    ImportResponse importar(ClienteAutenticado cliente, @Valid @RequestBody ImportRequest r) {
        var resultado = importar.importar(cliente.id(), r.contacts().stream()
                .map(c -> new ConexaoWhatsApp.ContatoWhatsApp(c.phone(), c.name())).toList());
        return new ImportResponse(resultado.importados(), resultado.ignorados());
    }

    record PhoneContactResponse(String phone, String name, boolean alreadySaved) {
    }

    record ImportRequest(@NotEmpty List<@Valid PhoneContact> contacts) {

        record PhoneContact(@NotBlank String phone, String name) {
        }
    }

    record ImportResponse(int imported, int skipped) {
    }

    @GetMapping
    @Operation(summary = "Lista os contatos do cliente")
    List<RecipientResponse> listar(ClienteAutenticado cliente) {
        return destinatarios.listar(cliente.id()).stream().map(RecipientResponse::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastra um contato")
    RecipientResponse cadastrar(ClienteAutenticado cliente, @Valid @RequestBody RecipientRequest r) {
        return RecipientResponse.de(destinatarios.cadastrar(cliente.id(), r.tipo(), r.name(), r.phone(), r.aceitaOfertas()));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhes de um contato")
    RecipientResponse buscar(ClienteAutenticado cliente, @PathVariable UUID id) {
        return RecipientResponse.de(destinatarios.buscar(cliente.id(), id));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Altera um contato")
    RecipientResponse alterar(ClienteAutenticado cliente, @PathVariable UUID id, @Valid @RequestBody RecipientRequest r) {
        return RecipientResponse.de(destinatarios.alterar(cliente.id(), id, r.tipo(), r.name(), r.phone(), r.aceitaOfertas()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Exclui um contato sem mensagens")
    void excluir(ClienteAutenticado cliente, @PathVariable UUID id) {
        destinatarios.excluir(cliente.id(), id);
    }
}
