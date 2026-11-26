package br.com.archat.aplicacao.entrega;

import br.com.archat.aplicacao.integracao.DadosDosEventos;
import br.com.archat.aplicacao.porta.CatalogoWhatsApp;
import br.com.archat.aplicacao.porta.NotificadorDeIntegracao;
import br.com.archat.dominio.integracao.EventoDeIntegracao;
import br.com.archat.aplicacao.porta.CatalogoWhatsApp.AtualizacaoDePedido;
import br.com.archat.aplicacao.porta.CatalogoWhatsApp.ItemDoPedido;
import br.com.archat.aplicacao.porta.CatalogoWhatsApp.PedidoParaEnvio;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.EntregaRepositorio;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.entrega.ConfiguracaoDeEntrega;
import br.com.archat.dominio.entrega.Entrega;
import br.com.archat.dominio.entrega.ItemDeEntrega;
import br.com.archat.dominio.entrega.OrigemEntrega;
import br.com.archat.dominio.entrega.StatusEntrega;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.excecao.RegraDeNegocioException;
import br.com.archat.dominio.mensagem.Mensagem;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Entregas pelo WhatsApp: o operador registra o pedido ao encerrar o atendimento (ou o pedido chega pelo carrinho do
 * catálogo); o responsável da conta confirma que saiu, e o cliente recebe o aviso "a caminho" na própria conversa, e
 * depois que foi entregue, e acompanha as demais. Com o pedido nativo do WhatsApp Business, o status é atualizado no
 * cartão do pedido; sem ele, o aviso vai em texto. Os avisos ficam no histórico como mensagens automáticas e não
 * são cobrados. Se o envio falhar, a entrega muda de estado do mesmo jeito e a falha fica registrada.
 */
public class GerenciarEntregasUseCase {
    public static final String ENDERECO_A_CONFIRMAR = "A confirmar com o cliente";

    private final EntregaRepositorio entregas;
    private final ConversaRepositorio conversas;
    private final DestinatarioRepositorio destinatarios;
    private final MensagemRepositorio mensagens;
    private final ConexaoWhatsApp canal;
    private final CatalogoWhatsApp catalogo;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;
    private NotificadorDeIntegracao integracoes = NotificadorDeIntegracao.NENHUM;

    public GerenciarEntregasUseCase(EntregaRepositorio entregas, ConversaRepositorio conversas,
                                    DestinatarioRepositorio destinatarios, MensagemRepositorio mensagens,
                                    ConexaoWhatsApp canal, CatalogoWhatsApp catalogo, UnidadeDeTrabalho unidadeDeTrabalho,
                                    Clock relogio) {
        this.entregas = entregas;
        this.conversas = conversas;
        this.destinatarios = destinatarios;
        this.mensagens = mensagens;
        this.canal = canal;
        this.catalogo = catalogo;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public record NovaEntrega(UUID conversaId, UUID operadorId, String endereco, String pedido, List<ItemDeEntrega> itens,
                              BigDecimal valor, String pagamento, String observacao) {
    }

    public record Resultado(Entrega entrega, Boolean avisoEnviado, boolean pelaPedidoNativo) {
    }

    public record Resumo(Map<StatusEntrega, Long> porStatus, Long minutosMedioAteEntregar, Long minutosMaisLongaNaRua) {
    }

    public static void validar(String endereco, String pedido, List<ItemDeEntrega> itens, BigDecimal valor, String pagamento,
                               String observacao) {
        Entrega.validarDados(endereco, pedido, itens, valor, pagamento, observacao);
    }

    public Entrega registrar(UUID clienteId, NovaEntrega n) {
        Conversa conversa = conversaDaEmpresa(clienteId, n.conversaId());
        Destinatario contato = contatoDa(conversa);
        Entrega entrega = Entrega.nova(clienteId, contato.id(), conversa.id(), n.operadorId(), contato.nome(),
                contato.telefone(), n.endereco(), n.pedido(), n.itens(), n.valor(), n.pagamento(), n.observacao(),
                OrigemEntrega.OPERADOR, null, relogio.instant());
        unidadeDeTrabalho.executar(() -> entregas.salvar(entrega));
        return entrega;
    }

    public Entrega registrarPedidoDoCatalogo(UUID clienteId, UUID conversaId, List<ItemDeEntrega> itens, String mensagemDoPedido,
                                             UUID mensagemId) {
        Conversa conversa = conversaDaEmpresa(clienteId, conversaId);
        Destinatario contato = contatoDa(conversa);
        Entrega entrega = Entrega.nova(clienteId, contato.id(), conversa.id(), null, contato.nome(), contato.telefone(),
                ENDERECO_A_CONFIRMAR, null, itens, null, null, null, OrigemEntrega.CATALOGO,
                mensagemDoPedido == null || mensagemDoPedido.length() <= 1000 ? mensagemDoPedido : mensagemDoPedido.substring(0, 1000),
                relogio.instant());
        unidadeDeTrabalho.executar(() -> {
            entregas.salvar(entrega);
            entregas.vincularMensagem(entrega.id(), mensagemId);
        });
        return entrega;
    }

    public Map<String, String> imagensDosProdutos(UUID clienteId, java.util.Set<String> produtoIds) {
        Map<String, String> fotos = new java.util.HashMap<>();
        try {
            String cursor = null;
            for (int pagina = 0; pagina < 8 && fotos.size() < produtoIds.size(); pagina++) {
                var r = catalogo.listar(clienteId, cursor);
                for (var p : r.produtos()) {
                    if (produtoIds.contains(p.id()) && p.imagem() != null && !p.imagem().isBlank()) {
                        fotos.put(p.id(), p.imagem());
                    }
                }
                cursor = r.proximoCursor();
                if (cursor == null) {
                    break;
                }
            }
        } catch (RuntimeException e) {
            return fotos;
        }
        return fotos;
    }

    public Map<UUID, List<ItemDeEntrega>> itensPorMensagem(java.util.Collection<UUID> mensagemIds) {
        return entregas.itensPorMensagem(mensagemIds);
    }

    public boolean vincularPedidoNativo(UUID clienteId, String telefone, String referencia, String requisicao) {
        String digitos = telefone == null ? "" : telefone.replaceAll("\\D", "");
        if (digitos.length() < 8 || referencia == null || requisicao == null) {
            return false;
        }
        Optional<Entrega> aberta = entregas.abertaPorFinalDoTelefone(clienteId, digitos.substring(digitos.length() - 8));
        aberta.ifPresent(e -> {
            e.vincularPedidoNativo(referencia, requisicao);
            unidadeDeTrabalho.executar(() -> entregas.salvar(e));
        });
        return aberta.isPresent();
    }

    public Entrega corrigir(UUID clienteId, UUID entregaId, String endereco, String observacao) {
        return unidadeDeTrabalho.executar(() -> {
            Entrega entrega = buscar(clienteId, entregaId);
            entrega.corrigirDados(endereco, observacao);
            entregas.salvar(entrega);
            return entrega;
        });
    }

    public void enviarCobranca(UUID clienteId, UUID entregaId, BigDecimal frete) {
        Entrega entrega = buscar(clienteId, entregaId);
        if (!entrega.status().emAberto()) {
            throw new RegraDeNegocioException("A entrega já foi encerrada");
        }
        if (catalogo == null) {
            throw new RegraDeNegocioException("O catálogo do WhatsApp não está disponível neste modo");
        }
        ConfiguracaoDeEntrega config = configuracao(clienteId);
        List<ItemDoPedido> itens = entrega.itens().isEmpty()
                ? List.of(new ItemDoPedido(null, entrega.pedido().length() > 200 ? entrega.pedido().substring(0, 200) : entrega.pedido(), 1,
                        entrega.valor() == null ? BigDecimal.ZERO : entrega.valor()))
                : entrega.itens().stream().map(i -> new ItemDoPedido(i.produtoId(), i.nome(), i.quantidade(), i.valorUnitario())).toList();
        catalogo.enviarPedido(clienteId, entrega.telefone(), new PedidoParaEnvio(itens, frete, config.pixChave(),
                config.pixTipo(), config.pixNome(), config.aceitaCartao()));
    }

    public GerenciarEntregasUseCase comIntegracoes(NotificadorDeIntegracao notificador) {
        this.integracoes = notificador == null ? NotificadorDeIntegracao.NENHUM : notificador;
        return this;
    }

    public Resultado saiu(UUID clienteId, UUID entregaId) {
        Entrega entrega = buscar(clienteId, entregaId);
        ConfiguracaoDeEntrega config = configuracao(clienteId);
        entrega.saiu(relogio.instant());
        unidadeDeTrabalho.executar(() -> {
            entregas.salvar(entrega);
            integracoes.notificar(clienteId, EventoDeIntegracao.ENTREGA_SAIU, DadosDosEventos.entrega(entrega));
        });
        String texto = config.saidaPara(primeiroNome(entrega.nomeCliente()));
        boolean nativo = config.avisarSaida() && atualizarPedidoNativo(entrega, "shipped", "Seu pedido saiu para entrega.");
        Boolean enviado = !config.avisarSaida() ? null : nativo || avisar(entrega, texto, true);
        if (config.avisarSaida() && nativo) {
            registrarNaConversa(entrega, texto);
        }
        entrega.registrarAvisoDaSaida(enviado);
        unidadeDeTrabalho.executar(() -> entregas.salvar(entrega));
        return new Resultado(entrega, enviado, nativo);
    }

    public Resultado entregue(UUID clienteId, UUID entregaId) {
        Entrega entrega = buscar(clienteId, entregaId);
        ConfiguracaoDeEntrega config = configuracao(clienteId);
        entrega.entregue(relogio.instant());
        unidadeDeTrabalho.executar(() -> {
            entregas.salvar(entrega);
            integracoes.notificar(clienteId, EventoDeIntegracao.ENTREGA_CONCLUIDA, DadosDosEventos.entrega(entrega));
        });
        String texto = config.entreguePara(primeiroNome(entrega.nomeCliente()));
        boolean nativo = config.avisarEntregue() && atualizarPedidoNativo(entrega, "completed", "Seu pedido foi entregue.");
        Boolean enviado = !config.avisarEntregue() ? null : nativo || avisar(entrega, texto, true);
        if (config.avisarEntregue() && nativo) {
            registrarNaConversa(entrega, texto);
        }
        entrega.registrarAvisoDaEntrega(enviado);
        unidadeDeTrabalho.executar(() -> entregas.salvar(entrega));
        return new Resultado(entrega, enviado, nativo);
    }

    public Entrega cancelar(UUID clienteId, UUID entregaId, String motivo) {
        Entrega entrega = unidadeDeTrabalho.executar(() -> {
            Entrega e = buscar(clienteId, entregaId);
            e.cancelar(relogio.instant(), motivo);
            entregas.salvar(e);
            return e;
        });
        atualizarPedidoNativo(entrega, "canceled", null);
        return entrega;
    }

    public Entrega buscar(UUID clienteId, UUID entregaId) {
        return entregas.buscarPorId(entregaId).filter(e -> e.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Entrega"));
    }

    public List<Entrega> listar(UUID clienteId, StatusEntrega status, Instant de, Instant ate, String busca) {
        return entregas.listar(clienteId, status, de, ate, busca);
    }

    public Resumo resumo(UUID clienteId) {
        Map<StatusEntrega, Long> contagem = new EnumMap<>(StatusEntrega.class);
        for (StatusEntrega s : StatusEntrega.values()) {
            contagem.put(s, 0L);
        }
        List<Entrega> todas = entregas.listar(clienteId, null, null, null, null);
        todas.forEach(e -> contagem.merge(e.status(), 1L, Long::sum));
        Instant agora = relogio.instant();
        List<Long> minutosDeRota = todas.stream().filter(e -> e.status() == StatusEntrega.ENTREGUE && e.saiuEm() != null)
                .map(e -> Duration.between(e.saiuEm(), e.entregueEm()).toMinutes()).toList();
        Long medio = minutosDeRota.isEmpty() ? null : Math.round(minutosDeRota.stream().mapToLong(Long::longValue).average().orElse(0));
        Long maisLonga = todas.stream().filter(e -> e.status() == StatusEntrega.A_CAMINHO && e.saiuEm() != null)
                .map(e -> Duration.between(e.saiuEm(), agora).toMinutes()).max(Long::compare).orElse(null);
        return new Resumo(contagem, medio, maisLonga);
    }

    public ConfiguracaoDeEntrega configuracao(UUID clienteId) {
        return entregas.buscarConfiguracao(clienteId).orElseGet(() -> ConfiguracaoDeEntrega.padrao(clienteId));
    }

    public ConfiguracaoDeEntrega configurar(ConfiguracaoDeEntrega nova) {
        unidadeDeTrabalho.executar(() -> entregas.salvar(nova));
        return nova;
    }

    private boolean atualizarPedidoNativo(Entrega entrega, String statusDoPedido, String mensagem) {
        if (catalogo == null || entrega.referenciaPedido() == null || entrega.requisicaoPedido() == null
                || entrega.mensagemDoPedido() == null || entrega.itens().isEmpty()) {
            return false;
        }
        try {
            List<ItemDoPedido> itens = entrega.itens().stream()
                    .map(i -> new ItemDoPedido(i.produtoId(), i.nome(), i.quantidade(), i.valorUnitario())).toList();
            catalogo.atualizarStatusDoPedido(entrega.clienteId(), entrega.telefone(), new AtualizacaoDePedido(
                    entrega.mensagemDoPedido(), entrega.referenciaPedido(), entrega.requisicaoPedido(), statusDoPedido,
                    "pending", itens, null, mensagem));
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private void registrarNaConversa(Entrega entrega, String texto) {
        unidadeDeTrabalho.executar(() -> {
            Mensagem aviso = Mensagem.automatica(entrega.conversaId(), texto, relogio.instant());
            mensagens.salvar(aviso);
            conversas.buscarPorId(entrega.conversaId()).ifPresent(c -> {
                c.registrar(aviso);
                conversas.salvar(c);
            });
        });
    }

    private boolean avisar(Entrega entrega, String texto, boolean registrar) {
        if (registrar) {
            registrarNaConversa(entrega, texto);
        }
        if (canal == null) {
            return false;
        }
        try {
            canal.enviarAvisoDoSistema(entrega.clienteId(), entrega.telefone(), texto);
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private Conversa conversaDaEmpresa(UUID clienteId, UUID conversaId) {
        return conversas.buscarPorId(conversaId).filter(c -> c.pertenceA(clienteId))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa"));
    }

    private Destinatario contatoDa(Conversa conversa) {
        return destinatarios.buscarPorId(conversa.destinatarioId())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Destinatário"));
    }

    private static String primeiroNome(String nome) {
        String limpo = nome == null ? "" : nome.strip();
        int espaco = limpo.indexOf(' ');
        return espaco > 0 ? limpo.substring(0, espaco) : limpo;
    }
}
