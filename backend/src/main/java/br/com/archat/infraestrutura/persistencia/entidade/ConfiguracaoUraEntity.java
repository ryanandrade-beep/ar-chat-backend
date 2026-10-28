package br.com.archat.infraestrutura.persistencia.entidade;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "configuracao_ura")
public class ConfiguracaoUraEntity {

    @Id
    @Column(name = "cliente_id")
    private UUID clienteId;
    private boolean ativa;
    @Column(name = "mensagem_boas_vindas")
    private String mensagemBoasVindas;
    @Column(name = "mensagem_pergunta")
    private String mensagemPergunta;
    @Column(name = "maximo_tentativas")
    private int maximoTentativas;
    @Column(name = "departamento_padrao_id")
    private UUID departamentoPadraoId;
    @Column(name = "ignorar_grupos")
    private boolean ignorarGrupos;

    protected ConfiguracaoUraEntity() {
    }

    public ConfiguracaoUraEntity(UUID clienteId, boolean ativa, String mensagemBoasVindas, String mensagemPergunta,
                                 int maximoTentativas, UUID departamentoPadraoId, boolean ignorarGrupos) {
        this.clienteId = clienteId;
        this.ativa = ativa;
        this.mensagemBoasVindas = mensagemBoasVindas;
        this.mensagemPergunta = mensagemPergunta;
        this.maximoTentativas = maximoTentativas;
        this.departamentoPadraoId = departamentoPadraoId;
        this.ignorarGrupos = ignorarGrupos;
    }

    public UUID getClienteId() { return clienteId; }
    public boolean isAtiva() { return ativa; }
    public String getMensagemBoasVindas() { return mensagemBoasVindas; }
    public String getMensagemPergunta() { return mensagemPergunta; }
    public int getMaximoTentativas() { return maximoTentativas; }
    public UUID getDepartamentoPadraoId() { return departamentoPadraoId; }
    public boolean isIgnorarGrupos() { return ignorarGrupos; }
}
