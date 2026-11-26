package br.com.archat.dominio.entrega;

import br.com.archat.dominio.excecao.RegraDeNegocioException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Uma entrega de pedido: o operador registra os dados ao encerrar o atendimento, o responsável da conta confirma
 * quando o pedido sai (o cliente é avisado no WhatsApp) e quando é entregue.
 */
public class Entrega {
    public static final Set<String> FORMAS_DE_PAGAMENTO = Set.of("pix", "cartao", "dinheiro", "outro");

    private final UUID id;
    private final UUID clienteId;
    private final UUID destinatarioId;
    private final UUID conversaId;
    private final UUID operadorId;
    private final String nomeCliente;
    private final String telefone;
    private String endereco;
    private final String pedido;
    private final BigDecimal valor;
    private final String pagamento;
    private String observacao;
    private StatusEntrega status;
    private final Instant criadaEm;
    private Instant saiuEm;
    private Instant entregueEm;
    private Instant canceladaEm;
    private String motivoCancelamento;
    private Boolean avisoSaidaEnviado;
    private Boolean avisoEntregaEnviado;
    private final OrigemEntrega origem;
    private final List<ItemDeEntrega> itens;
    private String referenciaPedido;
    private String requisicaoPedido;
    private String mensagemDoPedido;

    private Entrega(UUID id, UUID clienteId, UUID destinatarioId, UUID conversaId, UUID operadorId, String nomeCliente,
                    String telefone, String endereco, String pedido, BigDecimal valor, String pagamento, String observacao,
                    StatusEntrega status, Instant criadaEm, OrigemEntrega origem, List<ItemDeEntrega> itens) {
        this.origem = Objects.requireNonNull(origem);
        this.itens = List.copyOf(itens);
        this.id = Objects.requireNonNull(id);
        this.clienteId = Objects.requireNonNull(clienteId);
        this.destinatarioId = Objects.requireNonNull(destinatarioId);
        this.conversaId = Objects.requireNonNull(conversaId);
        this.operadorId = operadorId;
        this.nomeCliente = Objects.requireNonNull(nomeCliente);
        this.telefone = Objects.requireNonNull(telefone);
        this.endereco = endereco;
        this.pedido = pedido;
        this.valor = valor;
        this.pagamento = pagamento;
        this.observacao = observacao;
        this.status = Objects.requireNonNull(status);
        this.criadaEm = Objects.requireNonNull(criadaEm);
    }

    public static Entrega nova(UUID clienteId, UUID destinatarioId, UUID conversaId, UUID operadorId, String nomeCliente,
                               String telefone, String endereco, String pedido, List<ItemDeEntrega> itens, BigDecimal valor,
                               String pagamento, String observacao, OrigemEntrega origem, String mensagemDoPedido,
                               Instant agora) {
        List<ItemDeEntrega> lista = itens == null ? List.of() : itens;
        String textoDoPedido = pedido == null || pedido.isBlank() ? resumo(lista) : pedido;
        BigDecimal total = valor != null ? valor : lista.isEmpty() ? null : somar(lista);
        Entrega e = new Entrega(UUID.randomUUID(), clienteId, destinatarioId, conversaId, operadorId, nomeCliente, telefone,
                enderecoValido(endereco), pedidoValido(textoDoPedido), valorValido(total), pagamentoValido(pagamento),
                observacaoValida(observacao), StatusEntrega.AGUARDANDO, agora, origem == null ? OrigemEntrega.OPERADOR : origem,
                lista);
        e.mensagemDoPedido = mensagemDoPedido;
        return e;
    }

    private static String resumo(List<ItemDeEntrega> itens) {
        return itens.stream().map(i -> i.quantidade() + "x " + i.nome()).collect(java.util.stream.Collectors.joining(", "));
    }

    private static BigDecimal somar(List<ItemDeEntrega> itens) {
        return itens.stream().map(ItemDeEntrega::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public static Entrega reconstituir(UUID id, UUID clienteId, UUID destinatarioId, UUID conversaId, UUID operadorId,
                                       String nomeCliente, String telefone, String endereco, String pedido, BigDecimal valor,
                                       String pagamento, String observacao, StatusEntrega status, Instant criadaEm,
                                       Instant saiuEm, Instant entregueEm, Instant canceladaEm, String motivoCancelamento,
                                       Boolean avisoSaidaEnviado, Boolean avisoEntregaEnviado, OrigemEntrega origem,
                                       String referenciaPedido, String requisicaoPedido, String mensagemDoPedido,
                                       List<ItemDeEntrega> itens) {
        Entrega e = new Entrega(id, clienteId, destinatarioId, conversaId, operadorId, nomeCliente, telefone, endereco,
                pedido, valor, pagamento, observacao, status, criadaEm, origem, itens);
        e.referenciaPedido = referenciaPedido;
        e.requisicaoPedido = requisicaoPedido;
        e.mensagemDoPedido = mensagemDoPedido;
        e.saiuEm = saiuEm;
        e.entregueEm = entregueEm;
        e.canceladaEm = canceladaEm;
        e.motivoCancelamento = motivoCancelamento;
        e.avisoSaidaEnviado = avisoSaidaEnviado;
        e.avisoEntregaEnviado = avisoEntregaEnviado;
        return e;
    }

    public static void validarDados(String endereco, String pedido, List<ItemDeEntrega> itens, BigDecimal valor,
                                    String pagamento, String observacao) {
        enderecoValido(endereco);
        if (itens == null || itens.isEmpty() || (pedido != null && !pedido.isBlank())) {
            pedidoValido(pedido);
        }
        valorValido(valor);
        pagamentoValido(pagamento);
        observacaoValida(observacao);
    }

    private static String enderecoValido(String endereco) {
        String limpo = endereco == null ? "" : endereco.strip().replaceAll("\\s+", " ");
        if (limpo.length() < 5 || limpo.length() > 300) {
            throw new RegraDeNegocioException("Informe o endereço da entrega (de 5 a 300 caracteres)");
        }
        return limpo;
    }

    private static String pedidoValido(String pedido) {
        String limpo = pedido == null ? "" : pedido.strip();
        if (limpo.length() < 2 || limpo.length() > 1000) {
            throw new RegraDeNegocioException("Informe o pedido (de 2 a 1000 caracteres)");
        }
        return limpo;
    }

    private static BigDecimal valorValido(BigDecimal valor) {
        if (valor == null) {
            return null;
        }
        if (valor.signum() < 0 || valor.compareTo(new BigDecimal("99999.99")) > 0) {
            throw new RegraDeNegocioException("O valor do pedido deve ficar entre R$ 0,00 e R$ 99.999,99");
        }
        return valor.setScale(2, RoundingMode.HALF_EVEN);
    }

    private static String pagamentoValido(String pagamento) {
        if (pagamento == null || pagamento.isBlank()) {
            return null;
        }
        String chave = pagamento.strip().toLowerCase();
        if (!FORMAS_DE_PAGAMENTO.contains(chave)) {
            throw new RegraDeNegocioException("A forma de pagamento deve ser pix, cartao, dinheiro ou outro");
        }
        return chave;
    }

    private static String observacaoValida(String observacao) {
        if (observacao == null || observacao.isBlank()) {
            return null;
        }
        String limpa = observacao.strip();
        if (limpa.length() > 500) {
            throw new RegraDeNegocioException("A observação da entrega deve ter no máximo 500 caracteres");
        }
        return limpa;
    }

    public void saiu(Instant agora) {
        mudarPara(StatusEntrega.A_CAMINHO);
        saiuEm = agora;
    }

    public void entregue(Instant agora) {
        mudarPara(StatusEntrega.ENTREGUE);
        entregueEm = agora;
    }

    public void vincularPedidoNativo(String referencia, String requisicao) {
        this.referenciaPedido = referencia;
        this.requisicaoPedido = requisicao;
    }

    public void corrigirDados(String novoEndereco, String novaObservacao) {
        if (!status.emAberto()) {
            throw new RegraDeNegocioException("A entrega já foi encerrada e não pode ser alterada");
        }
        this.endereco = enderecoValido(novoEndereco);
        this.observacao = observacaoValida(novaObservacao);
    }

    public void registrarAvisoDaSaida(Boolean enviado) {
        avisoSaidaEnviado = enviado;
    }

    public void registrarAvisoDaEntrega(Boolean enviado) {
        avisoEntregaEnviado = enviado;
    }

    public void cancelar(Instant agora, String motivo) {
        String limpo = motivo == null ? null : motivo.strip();
        if (limpo != null && limpo.length() > 300) {
            throw new RegraDeNegocioException("O motivo deve ter no máximo 300 caracteres");
        }
        mudarPara(StatusEntrega.CANCELADA);
        canceladaEm = agora;
        motivoCancelamento = limpo == null || limpo.isEmpty() ? null : limpo;
    }

    private void mudarPara(StatusEntrega destino) {
        if (!status.podeIrPara(destino)) {
            throw new RegraDeNegocioException("A entrega está %s e não pode passar para %s".formatted(rotulo(status), rotulo(destino)));
        }
        status = destino;
    }

    private static String rotulo(StatusEntrega s) {
        return switch (s) {
            case AGUARDANDO -> "aguardando";
            case A_CAMINHO -> "a caminho";
            case ENTREGUE -> "entregue";
            case CANCELADA -> "cancelada";
        };
    }

    public boolean pertenceA(UUID clienteId) {
        return this.clienteId.equals(clienteId);
    }

    public UUID id() { return id; }
    public UUID clienteId() { return clienteId; }
    public UUID destinatarioId() { return destinatarioId; }
    public UUID conversaId() { return conversaId; }
    public UUID operadorId() { return operadorId; }
    public String nomeCliente() { return nomeCliente; }
    public String telefone() { return telefone; }
    public String endereco() { return endereco; }
    public String pedido() { return pedido; }
    public BigDecimal valor() { return valor; }
    public String pagamento() { return pagamento; }
    public String observacao() { return observacao; }
    public StatusEntrega status() { return status; }
    public Instant criadaEm() { return criadaEm; }
    public Instant saiuEm() { return saiuEm; }
    public Instant entregueEm() { return entregueEm; }
    public Instant canceladaEm() { return canceladaEm; }
    public String motivoCancelamento() { return motivoCancelamento; }
    public Boolean avisoSaidaEnviado() { return avisoSaidaEnviado; }
    public Boolean avisoEntregaEnviado() { return avisoEntregaEnviado; }
    public OrigemEntrega origem() { return origem; }
    public List<ItemDeEntrega> itens() { return itens; }
    public String referenciaPedido() { return referenciaPedido; }
    public String requisicaoPedido() { return requisicaoPedido; }
    public String mensagemDoPedido() { return mensagemDoPedido; }
}
