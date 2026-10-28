package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.campanha.ConsultarCampanhasUseCase;
import br.com.archat.aplicacao.campanha.DispararCampanhaUseCase;
import br.com.archat.apresentacao.dto.ApiEnums;
import br.com.archat.apresentacao.dto.MensagemDtos.MediaRequest;
import br.com.archat.apresentacao.dto.CampanhaDtos.CampaignCreatedResponse;
import br.com.archat.apresentacao.dto.CampanhaDtos.CampaignResponse;
import br.com.archat.apresentacao.dto.CampanhaDtos.CreateCampaignRequest;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/campaigns")
@Tag(name = "Campanhas", description = "Disparo de uma mensagem para vários contatos que aceitam ofertas")
class CampanhaController {

    private final DispararCampanhaUseCase disparar;
    private final ConsultarCampanhasUseCase consultar;

    CampanhaController(DispararCampanhaUseCase disparar, ConsultarCampanhasUseCase consultar) {
        this.disparar = disparar;
        this.consultar = consultar;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Dispara uma campanha: cobra tudo de uma vez ou recusa sem cobrar (402)")
    CampaignCreatedResponse disparar(ClienteAutenticado cliente, @Valid @RequestBody CreateCampaignRequest r) {
        cliente.exigirGestor();
        ApiEnums.exigirCanalDisponivel(r.channel());
        return CampaignCreatedResponse.de(disparar.executar(new DispararCampanhaUseCase.Comando(cliente.id(), r.name(),
                r.content(), MediaRequest.paraDominio(r.media()), ApiEnums.prioridade(r.priority()), r.recipientIds(),
                r.todosQueAceitam())));
    }

    @GetMapping
    @Operation(summary = "Campanhas do cliente com o andamento das mensagens")
    List<CampaignResponse> listar(ClienteAutenticado cliente) {
        cliente.exigirGestor();
        return consultar.listar(cliente.id()).stream().map(CampaignResponse::de).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Andamento de uma campanha")
    CampaignResponse buscar(ClienteAutenticado cliente, @PathVariable UUID id) {
        cliente.exigirGestor();
        return CampaignResponse.de(consultar.buscar(cliente.id(), id));
    }
}
