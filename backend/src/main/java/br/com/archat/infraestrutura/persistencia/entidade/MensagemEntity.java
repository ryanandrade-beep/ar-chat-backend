package br.com.archat.infraestrutura.persistencia.entidade;

import br.com.archat.dominio.mensagem.ContatoCompartilhado;
import br.com.archat.dominio.mensagem.Midia;
import br.com.archat.dominio.mensagem.Prioridade;
import br.com.archat.dominio.mensagem.TipoMidia;
import br.com.archat.dominio.mensagem.Remetente;
import br.com.archat.dominio.mensagem.StatusMensagem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mensagem")
public class MensagemEntity {

    @Id
    private UUID id;
    @Column(name = "conversa_id")
    private UUID conversaId;
    @Column(name = "campanha_id")
    private UUID campanhaId;
    @Enumerated(EnumType.STRING)
    @Column(name = "remetente_tipo")
    private Remetente remetente;
    private String conteudo;
    @Enumerated(EnumType.STRING)
    private Prioridade prioridade;
    @Enumerated(EnumType.STRING)
    private StatusMensagem status;
    private BigDecimal custo;
    private int tentativas;
    @Column(name = "id_externo")
    private String idExterno;
    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_midia")
    private TipoMidia tipoMidia;
    @Column(name = "url_midia")
    private String urlMidia;
    @Column(name = "nome_arquivo")
    private String nomeArquivo;
    @Column(name = "criada_em")
    private Instant criadaEm;
    @Column(name = "atualizada_em")
    private Instant atualizadaEm;

    private boolean automatica;

    @Column(name = "contato_nome")
    private String contatoNome;
    @Column(name = "contato_telefone")
    private String contatoTelefone;

    @Column(name = "operador_id")
    private UUID operadorId;
    private boolean interna;

    protected MensagemEntity() {
    }

    public MensagemEntity(UUID id, UUID conversaId, UUID campanhaId, Remetente remetente, String conteudo, Prioridade prioridade,
                          StatusMensagem status, BigDecimal custo, int tentativas, String idExterno,
                          Instant criadaEm, Instant atualizadaEm) {
        this.id = id;
        this.conversaId = conversaId;
        this.campanhaId = campanhaId;
        this.remetente = remetente;
        this.conteudo = conteudo;
        this.prioridade = prioridade;
        this.status = status;
        this.custo = custo;
        this.tentativas = tentativas;
        this.idExterno = idExterno;
        this.criadaEm = criadaEm;
        this.atualizadaEm = atualizadaEm;
    }

    public UUID getId() { return id; }
    public UUID getConversaId() { return conversaId; }
    public UUID getCampanhaId() { return campanhaId; }
    public Remetente getRemetente() { return remetente; }
    public String getConteudo() { return conteudo; }
    public Prioridade getPrioridade() { return prioridade; }
    public StatusMensagem getStatus() { return status; }
    public BigDecimal getCusto() { return custo; }
    public int getTentativas() { return tentativas; }
    public String getIdExterno() { return idExterno; }
    public Instant getCriadaEm() { return criadaEm; }
    public Instant getAtualizadaEm() { return atualizadaEm; }

    public void definirMidia(Midia midia) {
        this.tipoMidia = midia == null ? null : midia.tipo();
        this.urlMidia = midia == null ? null : midia.url();
        this.nomeArquivo = midia == null ? null : midia.nomeArquivo();
    }

    public void definirAutomatica(boolean automatica) {
        this.automatica = automatica;
    }

    public boolean isAutomatica() { return automatica; }

    public void definirAutoria(UUID operadorId, boolean interna) {
        this.operadorId = operadorId;
        this.interna = interna;
    }

    public UUID getOperadorId() { return operadorId; }
    public boolean isInterna() { return interna; }

    public void definirContato(ContatoCompartilhado contato) {
        this.contatoNome = contato == null ? null : contato.nome();
        this.contatoTelefone = contato == null ? null : contato.telefone();
    }

    public ContatoCompartilhado getContato() {
        return contatoNome == null ? null : new ContatoCompartilhado(contatoNome, contatoTelefone);
    }

    public Midia getMidia() {
        return tipoMidia == null ? null : new Midia(tipoMidia, urlMidia, nomeArquivo);
    }
}
