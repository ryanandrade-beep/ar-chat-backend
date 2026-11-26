package br.com.archat.aplicacao;

import br.com.archat.aplicacao.porta.MensagemRepositorio.ContagemPorCliente;
import br.com.archat.aplicacao.relatorio.Periodo;
import br.com.archat.aplicacao.relatorio.RelatorioCampanhasUseCase;
import br.com.archat.aplicacao.relatorio.RelatorioAtendimentosUseCase;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.atendimento.OrigemAtendimento;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.aplicacao.relatorio.RelatorioFaturamentoUseCase;
import br.com.archat.dominio.campanha.Campanha;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.Documento;
import br.com.archat.dominio.cliente.TipoDocumento;
import br.com.archat.dominio.cliente.TipoPlano;
import br.com.archat.dominio.financeiro.TipoTransacao;
import br.com.archat.dominio.financeiro.Transacao;
import br.com.archat.dominio.mensagem.Prioridade;
import br.com.archat.dominio.mensagem.StatusMensagem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RelatoriosTest {

    private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
    private final Fakes.Clientes clientes = new Fakes.Clientes();
    private final Fakes.Transacoes transacoes = new Fakes.Transacoes();
    private final Fakes.Mensagens mensagens = new Fakes.Mensagens();
    private final Fakes.Campanhas campanhas = new Fakes.Campanhas();
    private final Fakes.Atendimentos atendimentos = new Fakes.Atendimentos();
    private final Fakes.Conversas conversas = new Fakes.Conversas();
    private final Fakes.Destinatarios destinatarios = new Fakes.Destinatarios();

    private Cliente cliente(String nome, String cpf) {
        Cliente c = Cliente.novo(nome, new Documento(cpf, TipoDocumento.CPF), TipoPlano.PRE_PAGO, BigDecimal.ZERO,
                Instant.parse("2026-09-01T12:00:00Z"));
        clientes.salvar(c);
        return c;
    }

    private void transacao(Cliente c, TipoTransacao tipo, String valor, String saldoApos, String quando) {
        transacoes.salvar(new Transacao(UUID.randomUUID(), c.id(), null, tipo, new BigDecimal(valor),
                new BigDecimal(saldoApos), Instant.parse(quando)));
    }

    private static Periodo dias(String de, String ate) {
        return new Periodo(LocalDate.parse(de).atStartOfDay(BRASILIA).toInstant(),
                LocalDate.parse(ate).plusDays(1).atStartOfDay(BRASILIA).toInstant());
    }

    @Test
    void atendimentosFinalizadosTrazTempoDePrimeiraRespostaEsperaEAtendimento() {
        Cliente mercado = cliente("Mercado", "52998224725");
        Destinatario maria = Destinatario.novo(mercado.id(), "Maria", "44999990000", false);
        destinatarios.salvar(maria);
        Conversa conversa = Conversa.iniciar(mercado.id(), maria.id());
        conversas.salvar(conversa);

        Atendimento a = Atendimento.abertoPeloClienteFinal(conversa.id(), mercado.id(), Instant.parse("2026-10-01T13:00:00Z"));
        a.registrarEnvio(Instant.parse("2026-10-01T13:02:00Z"));
        a.registrarRecebimento();
        a.registrarEnvio(Instant.parse("2026-10-01T13:05:00Z"));
        a.finalizar(Instant.parse("2026-10-01T13:10:00Z"));
        atendimentos.salvar(a);
        Atendimento aindaAberto = Atendimento.abertoPeloClienteFinal(conversa.id(), mercado.id(), Instant.parse("2026-10-01T14:00:00Z"));
        atendimentos.salvar(aindaAberto);

        var r = new RelatorioAtendimentosUseCase(atendimentos, conversas, destinatarios, BRASILIA)
                .emitir(mercado.id(), dias("2026-10-01", "2026-10-01"));

        assertThat(r.total()).isEqualTo(1);
        assertThat(r.atendimentos().getFirst().destinatario()).isEqualTo("Maria");
        assertThat(r.tempoMedioPrimeiraResposta()).isEqualTo(java.time.Duration.ofMinutes(2));
        assertThat(r.esperaMedia()).isEqualTo(java.time.Duration.ofMinutes(2));
        assertThat(r.atendimentoMedio()).isEqualTo(java.time.Duration.ofMinutes(8));
        assertThat(r.mediaMensagens()).isEqualTo(4.0);
        assertThat(r.porDia()).containsEntry(LocalDate.parse("2026-10-01"), 1L);
    }

    @Test
    void faturamentoOrdenaClientesPelaReceitaLiquidaECalculaIndicadores() {
        Cliente mercado = cliente("Mercado", "52998224725");
        Cliente farmacia = cliente("Farmácia", "11144477735");
        transacao(mercado, TipoTransacao.DEBITO, "0.50", "9.50", "2026-10-01T13:00:00Z");
        transacao(mercado, TipoTransacao.DEBITO, "0.25", "9.25", "2026-10-01T14:00:00Z");
        transacao(mercado, TipoTransacao.DEBITO, "0.25", "9.00", "2026-10-01T15:00:00Z");
        transacao(mercado, TipoTransacao.ESTORNO, "0.25", "9.25", "2026-10-01T16:00:00Z");
        transacao(farmacia, TipoTransacao.DEBITO, "0.25", "4.75", "2026-10-01T13:00:00Z");
        mensagens.contagensPorCliente.add(new ContagemPorCliente(mercado.id(), Prioridade.URGENTE, StatusMensagem.ENTREGUE, 1));
        mensagens.contagensPorCliente.add(new ContagemPorCliente(mercado.id(), Prioridade.NORMAL, StatusMensagem.ENTREGUE, 1));
        mensagens.contagensPorCliente.add(new ContagemPorCliente(mercado.id(), Prioridade.NORMAL, StatusMensagem.FALHA, 1));
        mensagens.contagensPorCliente.add(new ContagemPorCliente(farmacia.id(), Prioridade.NORMAL, StatusMensagem.ENTREGUE, 1));

        var f = new RelatorioFaturamentoUseCase(clientes, transacoes, mensagens).emitir(dias("2026-10-01", "2026-10-01"));

        assertThat(f.clientes()).extracting(RelatorioFaturamentoUseCase.FaturamentoDoCliente::nome)
                .containsExactly("Mercado", "Farmácia");
        var linha = f.clientes().getFirst();
        assertThat(linha.receitaBruta()).isEqualByComparingTo("1.00");
        assertThat(linha.estornos()).isEqualByComparingTo("0.25");
        assertThat(linha.receitaLiquida()).isEqualByComparingTo("0.75");
        assertThat(linha.mensagens()).isEqualTo(3);
        assertThat(linha.percentualUrgentes()).isEqualByComparingTo("33.33");
        assertThat(linha.ticketMedio()).isEqualByComparingTo("0.38");
        assertThat(linha.participacao()).isEqualByComparingTo("75.00");
        assertThat(f.receitaLiquida()).isEqualByComparingTo("1.00");
    }

    @Test
    void desempenhoDaCampanhaCalculaTaxasEstornoECustoPorEntregue() {
        Cliente mercado = cliente("Mercado", "52998224725");
        Campanha ofertas = Campanha.nova(mercado.id(), "Ofertas", "Picanha", Prioridade.NORMAL, 10,
                Instant.parse("2026-10-01T13:00:00Z"));
        campanhas.salvar(ofertas);
        mensagens.contagensPorCampanha.put(ofertas.id(), Map.of(
                StatusMensagem.LIDA, 3L, StatusMensagem.ENTREGUE, 3L, StatusMensagem.ENVIADA, 1L,
                StatusMensagem.FALHA, 2L, StatusMensagem.ENFILEIRADA, 1L));

        var r = new RelatorioCampanhasUseCase(campanhas, mensagens).emitir(mercado.id(), dias("2026-10-01", "2026-10-01"));

        var d = r.campanhas().getFirst();
        assertThat(d.enviadas()).isEqualTo(7);
        assertThat(d.entregues()).isEqualTo(6);
        assertThat(d.lidas()).isEqualTo(3);
        assertThat(d.pendentes()).isEqualTo(1);
        assertThat(d.valorEstornado()).isEqualByComparingTo("0.50");
        assertThat(d.custoLiquido()).isEqualByComparingTo("2.00");
        assertThat(d.taxaEntrega()).isEqualByComparingTo("66.67");
        assertThat(d.taxaLeitura()).isEqualByComparingTo("50.00");
        assertThat(d.custoPorEntregue()).isEqualByComparingTo("0.33");
    }

    @Test
    void campanhaForaDoPeriodoNaoEntra() {
        Cliente mercado = cliente("Mercado", "52998224725");
        campanhas.salvar(Campanha.nova(mercado.id(), "Antiga", "x", Prioridade.NORMAL, 1, Instant.parse("2026-09-01T13:00:00Z")));

        assertThat(new RelatorioCampanhasUseCase(campanhas, mensagens)
                .emitir(mercado.id(), dias("2026-10-01", "2026-10-31")).campanhas()).isEmpty();
    }

    @Test
    void atendimentosFiltraPorOperadorOrigemEContato() {
        Cliente mercado = cliente("Mercado", "52998224725");
        UUID ana = UUID.randomUUID();
        Conversa daMaria = conversaFinalizada(mercado, "Maria", "44999990000", ana, true);
        conversaFinalizada(mercado, "João", "44999990001", null, false);
        var relatorio = new RelatorioAtendimentosUseCase(atendimentos, conversas, destinatarios, BRASILIA);
        Periodo outubro = dias("2026-10-01", "2026-10-01");

        assertThat(relatorio.emitir(mercado.id(), outubro).total()).isEqualTo(2);
        assertThat(relatorio.emitir(mercado.id(), outubro, new RelatorioAtendimentosUseCase.Filtro(ana, null, null))
                .atendimentos()).extracting(l -> l.atendimento().conversaId()).containsExactly(daMaria.id());
        assertThat(relatorio.emitir(mercado.id(), outubro,
                new RelatorioAtendimentosUseCase.Filtro(null, OrigemAtendimento.EMPRESA, null)).atendimentos())
                .extracting(RelatorioAtendimentosUseCase.AtendimentoFinalizado::destinatario).containsExactly("João");
        assertThat(relatorio.emitir(mercado.id(), outubro, new RelatorioAtendimentosUseCase.Filtro(null, null, " mar "))
                .total()).isEqualTo(1);
    }

    @Test
    void campanhasFiltraPorPrioridadeConteudoENome() {
        Cliente mercado = cliente("Mercado", "52998224725");
        Instant dia = Instant.parse("2026-10-01T13:00:00Z");
        campanhas.salvar(Campanha.nova(mercado.id(), "Ofertas da semana", "Picanha", Prioridade.NORMAL, 10, dia));
        campanhas.salvar(Campanha.nova(mercado.id(), "Aviso de pedido", "Pronto", Prioridade.URGENTE, 2, dia));
        var relatorio = new RelatorioCampanhasUseCase(campanhas, mensagens);
        Periodo outubro = dias("2026-10-01", "2026-10-01");

        assertThat(relatorio.emitir(mercado.id(), outubro,
                new RelatorioCampanhasUseCase.Filtro(Prioridade.URGENTE, null, null)).campanhas())
                .extracting(d -> d.campanha().nome()).containsExactly("Aviso de pedido");
        assertThat(relatorio.emitir(mercado.id(), outubro, new RelatorioCampanhasUseCase.Filtro(null, true, null))
                .campanhas()).isEmpty();
        assertThat(relatorio.emitir(mercado.id(), outubro, new RelatorioCampanhasUseCase.Filtro(null, false, "OFERTAS"))
                .totalDestinatarios()).isEqualTo(10);
    }

    @Test
    void faturamentoFiltraPorNomeDoCliente() {
        Cliente mercado = cliente("Mercado", "52998224725");
        Cliente farmacia = cliente("Farmácia", "11144477735");
        transacao(mercado, TipoTransacao.DEBITO, "0.50", "9.50", "2026-10-01T13:00:00Z");
        transacao(farmacia, TipoTransacao.DEBITO, "0.25", "4.75", "2026-10-01T13:00:00Z");

        var f = new RelatorioFaturamentoUseCase(clientes, transacoes, mensagens)
                .emitir(dias("2026-10-01", "2026-10-01"), new RelatorioFaturamentoUseCase.Filtro(TipoPlano.PRE_PAGO, "farm"));

        assertThat(f.clientes()).extracting(RelatorioFaturamentoUseCase.FaturamentoDoCliente::nome)
                .containsExactly("Farmácia");
        assertThat(f.receitaLiquida()).isEqualByComparingTo("0.25");
        assertThat(f.clientes().getFirst().participacao()).isEqualByComparingTo("100.00");
    }

    private Conversa conversaFinalizada(Cliente cliente, String nome, String telefone, UUID operador, boolean doCliente) {
        Destinatario contato = Destinatario.novo(cliente.id(), nome, telefone, false);
        destinatarios.salvar(contato);
        Conversa conversa = Conversa.iniciar(cliente.id(), contato.id());
        conversa.atribuirA(operador);
        conversas.salvar(conversa);
        Instant inicio = Instant.parse("2026-10-01T13:00:00Z");
        Atendimento a = doCliente ? Atendimento.abertoPeloClienteFinal(conversa.id(), cliente.id(), inicio)
                : Atendimento.abertoPelaEmpresa(conversa.id(), cliente.id(), inicio);
        a.finalizar(inicio.plusSeconds(600));
        atendimentos.salvar(a);
        return conversa;
    }
}
