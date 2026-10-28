package br.com.archat.infraestrutura.persistencia.entidade;

import br.com.archat.dominio.plano.Assinatura;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "assinatura")
public class AssinaturaEntity {

    @Id
    @Column(name = "cliente_id")
    private UUID clienteId;
    @Column(name = "plano_id")
    private UUID planoId;
    @Enumerated(EnumType.STRING)
    private Assinatura.Situacao situacao;
    private Instant inicio;
    @Column(name = "ultima_cobranca")
    private Instant ultimaCobranca;

    protected AssinaturaEntity() {
    }

    public AssinaturaEntity(UUID clienteId, UUID planoId, Assinatura.Situacao situacao, Instant inicio, Instant ultimaCobranca) {
        this.clienteId = clienteId;
        this.planoId = planoId;
        this.situacao = situacao;
        this.inicio = inicio;
        this.ultimaCobranca = ultimaCobranca;
    }

    public UUID getClienteId() { return clienteId; }
    public UUID getPlanoId() { return planoId; }
    public Assinatura.Situacao getSituacao() { return situacao; }
    public Instant getInicio() { return inicio; }
    public Instant getUltimaCobranca() { return ultimaCobranca; }
}
