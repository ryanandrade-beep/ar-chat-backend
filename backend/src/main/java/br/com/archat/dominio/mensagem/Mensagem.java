package br.com.archat.dominio.mensagem;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public class Mensagem {

    public static final int TAMANHO_MAXIMO = 4096;

    private final UUID id;
    private final UUID conversaId;
    private final UUID campanhaId;
    private final Remetente remetente;
    private final String conteudo;
    private final Midia midia;
    private final Prioridade prioridade;
    private StatusMensagem status;
    private final BigDecimal custo;
    private int tentativas;
    private String idExterno;
    private final Instant criadaEm;
    private Instant atualizadaEm;
    private boolean automatica;
    private UUID operadorId;
    private boolean interna;
    private ContatoCompartilhado contato;

    private Mensagem(UUID id, UUID conversaId, UUID campanhaId, Remetente remetente, String conteudo, Midia midia,
                     Prioridade prioridade, StatusMensagem status, BigDecimal custo, int tentativas, String idExterno,
                     Instant criadaEm, Instant atualizadaEm) {
        this.id = Objects.requireNonNull(id);
        this.conversaId = Objects.requireNonNull(conversaId);
        this.campanhaId = campanhaId;
        this.remetente = Objects.requireNonNull(remetente);
        this.conteudo = conteudo == null ? "" : conteudo;
        this.midia = midia;
        this.prioridade = Objects.requireNonNull(prioridade);
        this.status = Objects.requireNonNull(status);
        this.custo = Objects.requireNonNull(custo);
        this.tentativas = tentativas;
        this.idExterno = idExterno;
        this.criadaEm = Objects.requireNonNull(criadaEm);
        this.atualizadaEm = atualizadaEm;
    }

    public static Mensagem novaDoCliente(UUID conversaId, String conteudo, Prioridade prioridade, Instant agora) {
        return novaDoCliente(conversaId, null, conteudo, prioridade, agora);
    }

    public static Mensagem novaDoCliente(UUID conversaId, UUID campanhaId, String conteudo, Prioridade prioridade,
                                         Instant agora) {
        return novaDoCliente(conversaId, campanhaId, conteudo, null, prioridade, agora);
    }

    public static Mensagem novaDoCliente(UUID conversaId, UUID campanhaId, String conteudo, Midia midia,
                                         Prioridade prioridade, Instant agora) {
        return novaDoCliente(conversaId, campanhaId, conteudo, midia, prioridade,
                prioridade == null ? null : prioridade.custo(), agora);
    }

    public static Mensagem novaDoCliente(UUID conversaId, UUID campanhaId, String conteudo, Midia midia,
                                         Prioridade prioridade, BigDecimal custo, Instant agora) {
        validarConteudo(conteudo, midia);
        if (prioridade == null) {
            throw new RegraDeNegocioException("A prioridade é obrigatória");
        }
        if (custo == null || custo.signum() <= 0) {
            throw new RegraDeNegocioException("Preço da mensagem inválido");
        }
        return new Mensagem(UUID.randomUUID(), conversaId, campanhaId, Remetente.CLIENTE,
                conteudo == null ? "" : conteudo.strip(), midia, prioridade, StatusMensagem.ENFILEIRADA,
                custo, 0, null, agora, agora);
    }

    public static void validarConteudo(String conteudo, Midia midia) {
        boolean semTexto = conteudo == null || conteudo.isBlank();
        if (midia == null && semTexto) {
            throw new RegraDeNegocioException("O conteúdo da mensagem é obrigatório");
        }
        if (midia != null && !midia.tipo().aceitaLegenda() && !semTexto) {
            throw new RegraDeNegocioException("Mensagens de áudio não aceitam texto (legenda)");
        }
        if (conteudo != null && conteudo.length() > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException("A mensagem deve ter no máximo " + TAMANHO_MAXIMO + " caracteres");
        }
    }

    public static Mensagem recebidaDoDestinatario(UUID conversaId, String conteudo, String idExterno, Instant quando) {
        return recebidaDoDestinatario(conversaId, conteudo, null, idExterno, quando);
    }

    public static Mensagem recebidaDoDestinatario(UUID conversaId, String conteudo, Midia midia, String idExterno,
                                                  Instant quando) {
        boolean semTexto = conteudo == null || conteudo.isBlank();
        String texto = semTexto ? (midia == null ? "[mensagem sem texto]" : "") : conteudo.strip();
        if (texto.length() > TAMANHO_MAXIMO) {
            texto = texto.substring(0, TAMANHO_MAXIMO);
        }
        return new Mensagem(UUID.randomUUID(), conversaId, null, Remetente.DESTINATARIO, texto, midia,
                Prioridade.NORMAL, StatusMensagem.ENTREGUE, BigDecimal.ZERO.setScale(2), 0, idExterno, quando, quando);
    }

    public static Mensagem reconstituir(UUID id, UUID conversaId, UUID campanhaId, Remetente remetente,
                                        String conteudo, Prioridade prioridade, StatusMensagem status,
                                        BigDecimal custo, int tentativas, String idExterno, Instant criadaEm,
                                        Instant atualizadaEm) {
        return reconstituir(id, conversaId, campanhaId, remetente, conteudo, null, prioridade, status, custo,
                tentativas, idExterno, criadaEm, atualizadaEm);
    }

    public static Mensagem reconstituir(UUID id, UUID conversaId, UUID campanhaId, Remetente remetente,
                                        String conteudo, Midia midia, Prioridade prioridade, StatusMensagem status,
                                        BigDecimal custo, int tentativas, String idExterno, Instant criadaEm,
                                        Instant atualizadaEm) {
        return new Mensagem(id, conversaId, campanhaId, remetente, conteudo, midia, prioridade, status, custo,
                tentativas, idExterno, criadaEm, atualizadaEm);
    }

    public static Mensagem compartilhandoContato(UUID conversaId, ContatoCompartilhado contato, Prioridade prioridade,
                                                 BigDecimal custo, Instant agora) {
        if (prioridade == null || custo == null || custo.signum() <= 0) {
            throw new RegraDeNegocioException("A prioridade é obrigatória");
        }
        Mensagem mensagem = new Mensagem(UUID.randomUUID(), conversaId, null, Remetente.CLIENTE, "", null, prioridade,
                StatusMensagem.ENFILEIRADA, custo, 0, null, agora, agora);
        mensagem.contato = java.util.Objects.requireNonNull(contato);
        return mensagem;
    }

    public static Mensagem contatoRecebido(UUID conversaId, ContatoCompartilhado contato, String idExterno, Instant quando) {
        Mensagem mensagem = new Mensagem(UUID.randomUUID(), conversaId, null, Remetente.DESTINATARIO, "", null,
                Prioridade.NORMAL, StatusMensagem.ENTREGUE, BigDecimal.ZERO.setScale(2), 0, idExterno, quando, quando);
        mensagem.contato = contato;
        return mensagem;
    }

    public Mensagem comContato(ContatoCompartilhado contato) {
        this.contato = contato;
        return this;
    }

    public static Mensagem notaInterna(UUID conversaId, String texto, UUID operadorId, Instant agora) {
        if (texto == null || texto.isBlank()) {
            throw new RegraDeNegocioException("Escreva a nota");
        }
        if (texto.length() > TAMANHO_MAXIMO) {
            throw new RegraDeNegocioException("A nota deve ter no máximo " + TAMANHO_MAXIMO + " caracteres");
        }
        Mensagem nota = new Mensagem(UUID.randomUUID(), conversaId, null, Remetente.CLIENTE, texto.strip(), null,
                Prioridade.NORMAL, StatusMensagem.ENVIADA, BigDecimal.ZERO.setScale(2), 0, null, agora, agora);
        nota.interna = true;
        nota.operadorId = operadorId;
        return nota;
    }

    public Mensagem escritaPor(UUID operadorId) {
        this.operadorId = operadorId;
        return this;
    }

    public Mensagem comAutoria(UUID operadorId, boolean interna) {
        this.operadorId = operadorId;
        this.interna = interna;
        return this;
    }

    public static Mensagem automatica(UUID conversaId, String texto, Instant agora) {
        Mensagem mensagem = new Mensagem(UUID.randomUUID(), conversaId, null, Remetente.CLIENTE, texto, null,
                Prioridade.NORMAL, StatusMensagem.ENVIADA, BigDecimal.ZERO.setScale(2), 0, null, agora, agora);
        mensagem.automatica = true;
        return mensagem;
    }

    public static Mensagem reconstituir(UUID id, UUID conversaId, UUID campanhaId, Remetente remetente,
                                        String conteudo, Midia midia, Prioridade prioridade, StatusMensagem status,
                                        BigDecimal custo, int tentativas, String idExterno, Instant criadaEm,
                                        Instant atualizadaEm, boolean automatica) {
        Mensagem mensagem = reconstituir(id, conversaId, campanhaId, remetente, conteudo, midia, prioridade, status,
                custo, tentativas, idExterno, criadaEm, atualizadaEm);
        mensagem.automatica = automatica;
        return mensagem;
    }

    public String resumo() {
        if (contato != null) {
            return "[Contato] " + contato.nome();
        }
        if (midia == null) {
            return conteudo;
        }
        String rotulo = switch (midia.tipo()) {
            case IMAGEM -> "[Imagem]";
            case DOCUMENTO -> "[Documento]";
            case AUDIO -> "[Áudio]";
            case VIDEO -> "[Vídeo]";
        };
        return conteudo.isBlank() ? rotulo : rotulo + " " + conteudo;
    }

    public void iniciarProcessamento(Instant agora) {
        mudarPara(StatusMensagem.PROCESSANDO, agora);
        tentativas++;
    }

    public void confirmarEnvio(String idNoCanal, Instant agora) {
        mudarPara(StatusMensagem.ENVIADA, agora);
        idExterno = idNoCanal;
    }

    public void confirmarEntrega(Instant agora) {
        mudarPara(StatusMensagem.ENTREGUE, agora);
    }

    public void confirmarLeitura(Instant agora) {
        mudarPara(StatusMensagem.LIDA, agora);
    }

    public void registrarFalhaDeEnvio(int maximoTentativas, Instant agora) {
        mudarPara(tentativas < maximoTentativas ? StatusMensagem.ENFILEIRADA : StatusMensagem.FALHA, agora);
    }

    public void devolverParaFila(Instant agora) {
        if (status == StatusMensagem.PROCESSANDO) {
            mudarPara(StatusMensagem.ENFILEIRADA, agora);
        }
    }

    public boolean falhouDefinitivamente() {
        return status == StatusMensagem.FALHA;
    }

    private void mudarPara(StatusMensagem destino, Instant agora) {
        if (!status.podeIrPara(destino)) {
            throw new RegraDeNegocioException(
                    "Mensagem não pode passar de %s para %s".formatted(status, destino));
        }
        status = destino;
        atualizadaEm = agora;
    }

    public UUID id() { return id; }
    public UUID conversaId() { return conversaId; }
    public UUID campanhaId() { return campanhaId; }
    public Remetente remetente() { return remetente; }
    public String conteudo() { return conteudo; }
    public Midia midia() { return midia; }
    public boolean automatica() { return automatica; }
    public UUID operadorId() { return operadorId; }
    public boolean interna() { return interna; }
    public ContatoCompartilhado contato() { return contato; }
    public Prioridade prioridade() { return prioridade; }
    public StatusMensagem status() { return status; }
    public BigDecimal custo() { return custo; }
    public int tentativas() { return tentativas; }
    public String idExterno() { return idExterno; }
    public Instant criadaEm() { return criadaEm; }
    public Instant atualizadaEm() { return atualizadaEm; }
}
