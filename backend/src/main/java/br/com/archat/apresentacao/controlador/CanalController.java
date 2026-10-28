package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.canal.ListarCanaisUseCase;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/channels")
@Tag(name = "Canais", description = "Canais de atendimento: WhatsApp Web (disponível) e WhatsApp Business (em breve)")
class CanalController {

    private final ListarCanaisUseCase canais;

    CanalController(ListarCanaisUseCase canais) {
        this.canais = canais;
    }

    @GetMapping
    @Operation(summary = "Lista os canais e quais estão disponíveis e conectados")
    List<ChannelResponse> listar(ClienteAutenticado cliente) {
        return canais.listar(cliente.id()).stream()
                .map(c -> new ChannelResponse(c.id(), c.nome(), c.descricao(), c.disponivel(), c.conectado(),
                        c.disponivel() ? null : "Em breve"))
                .toList();
    }

    record ChannelResponse(String id, String name, String description, boolean available, boolean connected,
                           String badge) {
    }
}
