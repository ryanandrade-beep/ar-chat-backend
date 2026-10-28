package br.com.archat.infraestrutura.persistencia.entidade;

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
@Table(name = "conversa")
public class ConversaEntity {

    @Id
    private UUID id;
    @Column(name = "cliente_id")
    private UUID clienteId;
    @Column(name = "destinatario_id")
    private UUID destinatarioId;
    @Column(name = "ultima_mensagem")
    private String ultimaMensagem;
    @Column(name = "data_ultima_mensagem")
    private Instant dataUltimaMensagem;
    @Column(name = "nao_lidas")
    private int naoLidas;
    @Enumerated(EnumType.STRING)
    @Column(name = "status_atendimento")
    private StatusAtendimento statusAtendimento;

    @Column(name = "operador_id")
    private UUID operadorId;

    protected ConversaEntity() {
    }

    public ConversaEntity(UUID id, UUID clienteId, UUID destinatarioId, String ultimaMensagem,
                          Instant dataUltimaMensagem, int naoLidas, StatusAtendimento statusAtendimento) {
        this.id = id;
        this.clienteId = clienteId;
        this.destinatarioId = destinatarioId;
        this.ultimaMensagem = ultimaMensagem;
        this.dataUltimaMensagem = dataUltimaMensagem;
        this.naoLidas = naoLidas;
        this.statusAtendimento = statusAtendimento;
    }

    public UUID getId() { return id; }
    public UUID getClienteId() { return clienteId; }
    public UUID getDestinatarioId() { return destinatarioId; }
    public String getUltimaMensagem() { return ultimaMensagem; }
    public Instant getDataUltimaMensagem() { return dataUltimaMensagem; }
    public int getNaoLidas() { return naoLidas; }
    public StatusAtendimento getStatusAtendimento() { return statusAtendimento; }

    public void definirOperador(UUID operadorId) {
        this.operadorId = operadorId;
    }

    public UUID getOperadorId() { return operadorId; }
}
