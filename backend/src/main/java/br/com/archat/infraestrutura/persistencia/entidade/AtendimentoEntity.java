package br.com.archat.infraestrutura.persistencia.entidade;

import br.com.archat.dominio.atendimento.OrigemAtendimento;
import br.com.archat.dominio.atendimento.StatusAtendimento;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "atendimento")
public class AtendimentoEntity {

    @Id
    private UUID id;
    @Column(name = "conversa_id")
    private UUID conversaId;
    @Column(name = "cliente_id")
    private UUID clienteId;
    @Enumerated(EnumType.STRING)
    private OrigemAtendimento origem;
    @Enumerated(EnumType.STRING)
    private StatusAtendimento status;
    @Column(name = "aberto_em")
    private Instant abertoEm;
    @Column(name = "iniciado_em")
    private Instant iniciadoEm;
    @Column(name = "primeira_resposta_em")
    private Instant primeiraRespostaEm;
    @Column(name = "finalizado_em")
    private Instant finalizadoEm;
    @Column(name = "observacao_encerramento")
    private String observacaoEncerramento;
    @Column(name = "mensagens_enviadas")
    private int mensagensEnviadas;
    @Column(name = "mensagens_recebidas")
    private int mensagensRecebidas;

    protected AtendimentoEntity() {
    }

    public AtendimentoEntity(UUID id, UUID conversaId, UUID clienteId, OrigemAtendimento origem,
                             StatusAtendimento status, Instant abertoEm, Instant iniciadoEm,
                             Instant primeiraRespostaEm, Instant finalizadoEm, int mensagensEnviadas,
                             int mensagensRecebidas, String observacaoEncerramento) {
        this.observacaoEncerramento = observacaoEncerramento;
        this.id = id;
        this.conversaId = conversaId;
        this.clienteId = clienteId;
        this.origem = origem;
        this.status = status;
        this.abertoEm = abertoEm;
        this.iniciadoEm = iniciadoEm;
        this.primeiraRespostaEm = primeiraRespostaEm;
        this.finalizadoEm = finalizadoEm;
        this.mensagensEnviadas = mensagensEnviadas;
        this.mensagensRecebidas = mensagensRecebidas;
    }

    public UUID getId() { return id; }
    public UUID getConversaId() { return conversaId; }
    public UUID getClienteId() { return clienteId; }
    public OrigemAtendimento getOrigem() { return origem; }
    public StatusAtendimento getStatus() { return status; }
    public Instant getAbertoEm() { return abertoEm; }
    public Instant getIniciadoEm() { return iniciadoEm; }
    public Instant getPrimeiraRespostaEm() { return primeiraRespostaEm; }
    public Instant getFinalizadoEm() { return finalizadoEm; }
    public String getObservacaoEncerramento() { return observacaoEncerramento; }
    public int getMensagensEnviadas() { return mensagensEnviadas; }
    public int getMensagensRecebidas() { return mensagensRecebidas; }
}
