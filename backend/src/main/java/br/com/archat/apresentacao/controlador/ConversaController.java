package br.com.archat.apresentacao.controlador;

import br.com.archat.aplicacao.atendimento.AtendimentoEmEquipeUseCase;
import br.com.archat.aplicacao.atendimento.GerenciarAtendimentoUseCase;
import br.com.archat.aplicacao.atendimento.ReceberMensagemUseCase;
import br.com.archat.aplicacao.conversa.ConsultarConversasUseCase;
import br.com.archat.aplicacao.departamento.GerenciarDepartamentosUseCase;
import br.com.archat.aplicacao.entrega.GerenciarEntregasUseCase;
import br.com.archat.dominio.entrega.ItemDeEntrega;
import br.com.archat.aplicacao.mensagem.ConsultarMensagensUseCase;
import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.aplicacao.porta.FiltroMensagens;
import br.com.archat.apresentacao.dto.ApiEnums;
import br.com.archat.apresentacao.dto.ConversaDtos.ConversationResponse;
import br.com.archat.apresentacao.dto.ConversaDtos.Participant;
import br.com.archat.apresentacao.dto.MensagemDtos.MessageResponse;
import br.com.archat.apresentacao.dto.MensagemDtos.SimulatedReplyRequest;
import br.com.archat.apresentacao.seguranca.AuditoriaDaRequisicao;
import br.com.archat.apresentacao.seguranca.ClienteAutenticado;
import br.com.archat.aplicacao.auditoria.RegistrarAuditoriaUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/conversations")
@Tag(name = "Atendimento", description = "Conversas e o status do atendimento: aguardando, em andamento, em espera e finalizado")
class ConversaController {

    private final ConsultarConversasUseCase conversas;
    private final ConsultarMensagensUseCase mensagens;
    private final GerenciarAtendimentoUseCase atendimento;
    private final ReceberMensagemUseCase receber;

    private final GerenciarOperadoresUseCase operadores;
    private final GerenciarDepartamentosUseCase departamentos;

    private final AtendimentoEmEquipeUseCase equipe;
    private final GerenciarEntregasUseCase entregas;
    private final RegistrarAuditoriaUseCase auditoria;

    ConversaController(ConsultarConversasUseCase conversas, ConsultarMensagensUseCase mensagens,
                       GerenciarAtendimentoUseCase atendimento, ReceberMensagemUseCase receber,
                       GerenciarOperadoresUseCase operadores, GerenciarDepartamentosUseCase departamentos, AtendimentoEmEquipeUseCase equipe,
                       GerenciarEntregasUseCase entregas, RegistrarAuditoriaUseCase auditoria) {
        this.entregas = entregas;
        this.auditoria = auditoria;
        this.equipe = equipe;
        this.operadores = operadores;
        this.departamentos = departamentos;
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.atendimento = atendimento;
        this.receber = receber;
    }

    @GetMapping
    @Operation(summary = "Conversas do cliente, da mais recente para a mais antiga; filtre por status "
            + "(waiting, in_progress, on_hold ou finished) e por responsável (assigned: me, none ou o id do operador)")
    List<ConversationResponse> listar(ClienteAutenticado cliente, @RequestParam(required = false) String search,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(required = false) String assigned,
                                      @RequestParam(required = false) UUID department) {
        Map<UUID, String> nomes = nomesDosOperadores(cliente);
        Map<UUID, String> setores = nomesDosDepartamentos(cliente);
        return conversas.listar(cliente.id(), search, status == null ? null : ApiEnums.atendimento(status)).stream()
                .filter(r -> "me".equals(assigned)
                        ? cliente.atendenteId().equals(r.conversa().operadorId())
                                || equipe.participantes(r.conversa().id()).contains(cliente.atendenteId())
                        : responsavelConfere(r.conversa().operadorId(), assigned, cliente))
                .filter(r -> department == null || department.equals(r.conversa().departamentoId()))
                .map(r -> ConversationResponse.de(r, nomes, setores, participantesDe(r.conversa().id(), nomes),
                        cicloDe(r.conversa().id()))).toList();
    }

    @PutMapping("/{id}/department")
    @Operation(summary = "Transfere a conversa para outro departamento (departmentId nulo tira o departamento)")
    ConversationResponse transferirDepartamento(ClienteAutenticado cliente, @PathVariable UUID id,
                                                @RequestBody DepartmentRequest r) {
        atendimento.definirDepartamento(cliente.id(), id, r.departmentId());
        return resposta(cliente, id);
    }

    record DepartmentRequest(UUID departmentId) {
    }

    @PostMapping("/{id}/join")
    @Operation(summary = "Participa do atendimento (responde junto com o responsável)")
    ConversationResponse participar(ClienteAutenticado cliente, @PathVariable UUID id) {
        equipe.participar(cliente.id(), id, cliente.atendenteId());
        return resposta(cliente, id);
    }

    @PostMapping("/{id}/leave")
    @Operation(summary = "Deixa de participar do atendimento")
    ConversationResponse sair(ClienteAutenticado cliente, @PathVariable UUID id) {
        equipe.sair(cliente.id(), id, cliente.atendenteId());
        return resposta(cliente, id);
    }

    @PostMapping("/{id}/notes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Nota interna para a equipe (não vai para o WhatsApp e não é cobrada)")
    MessageResponse nota(ClienteAutenticado cliente, @PathVariable UUID id, @Valid @RequestBody NoteRequest r) {
        return MessageResponse.de(equipe.notaInterna(cliente.id(), id, cliente.atendenteId(), r.content()));
    }

    @PostMapping("/{id}/transfer")
    @Operation(summary = "Transfere para outro operador e/ou departamento, com recado (nota interna) para quem recebe")
    ConversationResponse transferir(ClienteAutenticado cliente, @PathVariable UUID id, @RequestBody TransferRequest r) {
        equipe.transferir(cliente.id(), id, r.operatorId(), r.departmentId(), r.note(), cliente.atendenteId());
        return resposta(cliente, id);
    }

    record NoteRequest(@jakarta.validation.constraints.NotBlank String content) {
    }

    record TransferRequest(UUID operatorId, UUID departmentId, String note) {
    }

    private List<Participant> participantesDe(UUID conversaId, Map<UUID, String> nomes) {
        return equipe.participantes(conversaId).stream().map(id -> new Participant(id, nomes.getOrDefault(id, "?"))).toList();
    }

    private Map<UUID, String> nomesDosDepartamentos(ClienteAutenticado cliente) {
        Map<UUID, String> nomes = new java.util.HashMap<>();
        departamentos.listar(cliente.id()).forEach(d -> nomes.put(d.id(), d.nome()));
        return nomes;
    }

    @PostMapping("/{id}/assign")
    @Operation(summary = "Assume, transfere (operatorId) ou devolve para a fila (operatorId nulo) a conversa")
    ConversationResponse atribuir(ClienteAutenticado cliente, @PathVariable UUID id, @RequestBody AssignRequest r) {
        atendimento.atribuir(cliente.id(), id, r.operatorId(), cliente.operadorId());
        return resposta(cliente, id);
    }

    record AssignRequest(UUID operatorId) {
    }

    private static boolean responsavelConfere(UUID responsavel, String filtro, ClienteAutenticado cliente) {
        if (filtro == null || filtro.isBlank() || filtro.equals("all")) return true;
        if (filtro.equals("none")) return responsavel == null;
        if (filtro.equals("me")) return false;
        return responsavel != null && responsavel.toString().equals(filtro);
    }

    private Map<UUID, String> nomesDosOperadores(ClienteAutenticado cliente) {
        Map<UUID, String> nomes = new java.util.HashMap<>();
        operadores.listar(cliente.id()).forEach(o -> nomes.put(o.id(), o.nome()));
        return nomes;
    }

    private ConversationResponse resposta(ClienteAutenticado cliente, UUID conversaId) {
        Map<UUID, String> nomes = nomesDosOperadores(cliente);
        return ConversationResponse.de(conversas.buscar(cliente.id(), conversaId), nomes, nomesDosDepartamentos(cliente),
                participantesDe(conversaId, nomes), cicloDe(conversaId));
    }

    private br.com.archat.apresentacao.dto.ConversaDtos.Attendance cicloDe(UUID conversaId) {
        return br.com.archat.apresentacao.dto.ConversaDtos.Attendance.de(atendimento.cicloAtual(conversaId).orElse(null));
    }

    @GetMapping("/summary")
    @Operation(summary = "Quantidade de conversas em cada status (para as abas da tela de atendimento)")
    Map<String, Long> resumo(ClienteAutenticado cliente) {
        Map<String, Long> resumo = new LinkedHashMap<>();
        atendimento.resumo(cliente.id()).forEach((status, total) -> resumo.put(ApiEnums.atendimento(status), total));
        return resumo;
    }

    @PostMapping("/{id}/hold")
    @Operation(summary = "Coloca o atendimento em espera")
    ConversationResponse colocarEmEspera(ClienteAutenticado cliente, @PathVariable UUID id) {
        atendimento.colocarEmEspera(cliente.id(), id);
        return resposta(cliente, id);
    }

    @PostMapping("/{id}/resume")
    @Operation(summary = "Retoma um atendimento em espera")
    ConversationResponse retomar(ClienteAutenticado cliente, @PathVariable UUID id) {
        atendimento.retomar(cliente.id(), id);
        return resposta(cliente, id);
    }

    @PostMapping("/{id}/finish")
    @Operation(summary = "Finaliza o atendimento (destination=close, padrão) ou devolve a conversa para a fila geral, sem "
            + "departamento nem responsável (destination=general). note é uma observação opcional (até 500 caracteres) "
            + "que fica no histórico. Uma nova mensagem do cliente final depois de finalizar abre outro atendimento")
    ConversationResponse finalizar(ClienteAutenticado cliente, @PathVariable UUID id,
                                   @RequestBody(required = false) FinishRequest r) {
        String observacao = r == null ? null : r.note();
        var entrega = r == null ? null : r.delivery();
        List<ItemDeEntrega> itens = List.of();
        if (entrega != null) {
            if (r.destination() != null && !"close".equals(r.destination())) {
                throw new br.com.archat.dominio.excecao.RegraDeNegocioException("A entrega só pode ser gerada ao encerrar o atendimento");
            }
            itens = entrega.itens();
            GerenciarEntregasUseCase.validar(entrega.address(), entrega.order(), itens, entrega.value(), entrega.paymentMethod(),
                    entrega.note());
        }
        if (r != null && "general".equals(r.destination())) {
            atendimento.enviarParaGeral(cliente.id(), id, observacao, cliente.atendenteId());
            AuditoriaDaRequisicao.acao(br.com.archat.dominio.auditoria.AcaoDeAuditoria.ATENDIMENTO_ENVIADO_GERAL);
        } else if (r != null && r.destination() != null && !"close".equals(r.destination())) {
            throw new br.com.archat.dominio.excecao.RegraDeNegocioException("destination deve ser close ou general");
        } else {
            atendimento.finalizar(cliente.id(), id, observacao, cliente.atendenteId());
            if (entrega != null) {
                var criada = entregas.registrar(cliente.id(), new GerenciarEntregasUseCase.NovaEntrega(id, cliente.atendenteId(),
                        entrega.address(), entrega.order(), itens, entrega.value(), entrega.paymentMethod(), entrega.note()));
                auditoria.registrar(cliente.id(), cliente.atendenteId(), br.com.archat.dominio.auditoria.AcaoDeAuditoria.ENTREGA_CRIADA,
                        "Entrega de " + criada.nomeCliente() + " · " + criada.endereco(), "Valor R$ " + criada.valor());
                AuditoriaDaRequisicao.detalhe("Com entrega gerada");
            }
        }
        return resposta(cliente, id);
    }

    record FinishRequest(String destination, String note, DeliveryRequest delivery) {
    }

    record DeliveryRequest(String address, String order, List<DeliveryItemRequest> items, BigDecimal value, String paymentMethod,
                           String note) {
        List<ItemDeEntrega> itens() {
            return items == null ? List.of() : items.stream().map(i -> new ItemDeEntrega(i.productId(), i.name(),
                    i.quantity() == null ? 0 : i.quantity(), i.unitPrice() == null ? BigDecimal.ZERO : i.unitPrice())).toList();
        }
    }

    record DeliveryItemRequest(String productId, String name, Integer quantity, BigDecimal unitPrice) {
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Marca as mensagens recebidas da conversa como lidas (zera o contador)")
    ConversationResponse marcarComoLida(ClienteAutenticado cliente, @PathVariable UUID id) {
        atendimento.marcarComoLida(cliente.id(), id);
        return resposta(cliente, id);
    }

    @PostMapping("/{id}/simulate-reply")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Simula uma mensagem do cliente final nesta conversa (para testes e demonstração, "
            + "sem depender do webhook)")
    ConversationResponse simularResposta(ClienteAutenticado cliente, @PathVariable UUID id,
                                         @Valid @RequestBody SimulatedReplyRequest r) {
        receber.simularResposta(cliente.id(), id, r.content());
        return resposta(cliente, id);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Detalhes de uma conversa")
    ConversationResponse buscar(ClienteAutenticado cliente, @PathVariable UUID id) {
        return resposta(cliente, id);
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "Mensagens de uma conversa, em ordem cronológica")
    List<MessageResponse> mensagens(ClienteAutenticado cliente, @PathVariable UUID id) {
        var lista = mensagens.listar(new FiltroMensagens(cliente.id(), id, null, null));
        var pedidos = entregas.itensPorMensagem(lista.stream().map(m -> m.id()).toList());
        return lista.stream().map(m -> MessageResponse.de(m).comPedido(MessageResponse.Order.de(pedidos.get(m.id())))).toList();
    }
}
