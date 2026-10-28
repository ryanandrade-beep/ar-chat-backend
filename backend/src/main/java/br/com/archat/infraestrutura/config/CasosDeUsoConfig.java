package br.com.archat.infraestrutura.config;

import br.com.archat.aplicacao.administrador.GerenciarAdministradoresUseCase;
import br.com.archat.aplicacao.atendimento.GerenciarAtendimentoUseCase;
import br.com.archat.aplicacao.atendimento.ReceberMensagemUseCase;
import br.com.archat.aplicacao.atendimento.RegistroDeAtendimento;
import br.com.archat.aplicacao.autenticacao.AutenticarAdministradorUseCase;
import br.com.archat.aplicacao.autenticacao.AutenticarClienteUseCase;
import br.com.archat.aplicacao.campanha.ConsultarCampanhasUseCase;
import br.com.archat.aplicacao.campanha.DispararCampanhaUseCase;
import br.com.archat.aplicacao.canal.ListarCanaisUseCase;
import br.com.archat.aplicacao.cliente.AtualizarClienteUseCase;
import br.com.archat.aplicacao.cliente.CadastrarClienteUseCase;
import br.com.archat.aplicacao.cliente.ConsultarClientesUseCase;
import br.com.archat.aplicacao.cliente.ConsultarExtratoUseCase;
import br.com.archat.aplicacao.cliente.GerenciarFinanceiroUseCase;
import br.com.archat.aplicacao.cliente.ReiniciarConsumoMensalUseCase;
import br.com.archat.aplicacao.conta.CadastrarContaUseCase;
import br.com.archat.aplicacao.conta.RecuperarSenhaUseCase;
import br.com.archat.aplicacao.conversa.ConsultarConversasUseCase;
import br.com.archat.aplicacao.destinatario.GerenciarDestinatariosUseCase;
import br.com.archat.aplicacao.destinatario.ImportarContatosUseCase;
import br.com.archat.aplicacao.mensagem.AtualizarStatusPeloCanalUseCase;
import br.com.archat.aplicacao.mensagem.ConfirmarRecebimentoUseCase;
import br.com.archat.aplicacao.mensagem.ConsultarMensagensUseCase;
import br.com.archat.aplicacao.mensagem.EnviarMensagemUseCase;
import br.com.archat.aplicacao.mensagem.ProcessarMensagemUseCase;
import br.com.archat.aplicacao.mensagem.RecuperarFilaUseCase;
import br.com.archat.aplicacao.monitoramento.MonitorarFilaUseCase;
import br.com.archat.aplicacao.operador.ConsultarEquipeUseCase;
import br.com.archat.aplicacao.operador.GerenciarOperadoresUseCase;
import br.com.archat.aplicacao.porta.AdministradorRepositorio;
import br.com.archat.aplicacao.porta.AtendimentoRepositorio;
import br.com.archat.aplicacao.porta.CampanhaRepositorio;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.CodificadorSenha;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.aplicacao.porta.ContaDeAcessoRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.EnvioDeEmail;
import br.com.archat.aplicacao.porta.EnvioMensagemGateway;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.MetricasFila;
import br.com.archat.aplicacao.porta.OperadorRepositorio;
import br.com.archat.aplicacao.porta.ServicoToken;
import br.com.archat.aplicacao.porta.TransacaoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.aplicacao.relatorio.RelatorioAtendimentosUseCase;
import br.com.archat.aplicacao.relatorio.RelatorioCampanhasUseCase;
import br.com.archat.aplicacao.relatorio.RelatorioFaturamentoUseCase;
import br.com.archat.aplicacao.whatsapp.GerenciarConexaoWhatsAppUseCase;
import br.com.archat.infraestrutura.fila.FilaMensagensEmMemoria;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Monta os casos de uso. Eles são classes Java puras (sem anotações do Spring); aqui cada um recebe
 * as implementações concretas das portas de que depende.
 */
@Configuration
@EnableConfigurationProperties(ArChatPropriedades.class)
class CasosDeUsoConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    FilaMensagensEmMemoria filaMensagens(ArChatPropriedades propriedades) {
        return new FilaMensagensEmMemoria(propriedades.fila().urgentesSeguidas());
    }

    @Bean
    AutenticarClienteUseCase autenticarCliente(ClienteRepositorio clientes, ServicoToken tokens, Clock relogio,
                                               ArChatPropriedades propriedades, ContaDeAcessoRepositorio contas,
                                               CodificadorSenha codificador, OperadorRepositorio operadores) {
        return new AutenticarClienteUseCase(clientes, tokens, relogio, propriedades.token().validade(), contas,
                codificador, operadores);
    }

    @Bean
    ConsultarEquipeUseCase consultarEquipe(ContaDeAcessoRepositorio contas, OperadorRepositorio operadores) {
        return new ConsultarEquipeUseCase(contas, operadores);
    }

    @Bean
    GerenciarOperadoresUseCase gerenciarOperadores(OperadorRepositorio operadores, ContaDeAcessoRepositorio contas,
                                                   CodificadorSenha codificador, UnidadeDeTrabalho unidadeDeTrabalho,
                                                   Clock relogio) {
        return new GerenciarOperadoresUseCase(operadores, contas, codificador, unidadeDeTrabalho, relogio);
    }

    @Bean
    CadastrarContaUseCase cadastrarConta(ClienteRepositorio clientes, ContaDeAcessoRepositorio contas,
                                         CodificadorSenha codificador, ServicoToken tokens,
                                         UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio,
                                         ArChatPropriedades propriedades) {
        return new CadastrarContaUseCase(clientes, contas, codificador, tokens, unidadeDeTrabalho, relogio,
                propriedades.token().validade());
    }

    @Bean
    RecuperarSenhaUseCase recuperarSenha(ContaDeAcessoRepositorio contas, CodificadorSenha codificador,
                                         EnvioDeEmail email, ConexaoWhatsApp whatsapp,
                                         UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        return new RecuperarSenhaUseCase(contas, codificador, email, whatsapp, unidadeDeTrabalho, relogio);
    }

    @Bean
    AutenticarAdministradorUseCase autenticarAdministrador(AdministradorRepositorio administradores,
                                                           CodificadorSenha codificador, ServicoToken tokens,
                                                           Clock relogio, ArChatPropriedades propriedades) {
        return new AutenticarAdministradorUseCase(administradores, codificador, tokens, relogio,
                propriedades.token().validade());
    }

    @Bean
    CadastrarClienteUseCase cadastrarCliente(ClienteRepositorio clientes, TransacaoRepositorio transacoes,
                                             UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        return new CadastrarClienteUseCase(clientes, transacoes, unidadeDeTrabalho, relogio);
    }

    @Bean
    AtualizarClienteUseCase atualizarCliente(ClienteRepositorio clientes, UnidadeDeTrabalho unidadeDeTrabalho) {
        return new AtualizarClienteUseCase(clientes, unidadeDeTrabalho);
    }

    @Bean
    ConsultarClientesUseCase consultarClientes(ClienteRepositorio clientes) {
        return new ConsultarClientesUseCase(clientes);
    }

    @Bean
    ConsultarExtratoUseCase consultarExtrato(TransacaoRepositorio transacoes, Clock relogio) {
        return new ConsultarExtratoUseCase(transacoes, relogio);
    }

    @Bean
    ImportarContatosUseCase importarContatos(ConexaoWhatsApp conexao, DestinatarioRepositorio destinatarios,
                                             UnidadeDeTrabalho unidadeDeTrabalho) {
        return new ImportarContatosUseCase(conexao, destinatarios, unidadeDeTrabalho);
    }

    @Bean
    GerenciarFinanceiroUseCase gerenciarFinanceiro(ClienteRepositorio clientes, TransacaoRepositorio transacoes,
                                                   UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        return new GerenciarFinanceiroUseCase(clientes, transacoes, unidadeDeTrabalho, relogio);
    }

    @Bean
    ReiniciarConsumoMensalUseCase reiniciarConsumoMensal(ClienteRepositorio clientes,
                                                         UnidadeDeTrabalho unidadeDeTrabalho) {
        return new ReiniciarConsumoMensalUseCase(clientes, unidadeDeTrabalho);
    }

    @Bean
    GerenciarDestinatariosUseCase gerenciarDestinatarios(DestinatarioRepositorio destinatarios,
                                                         MensagemRepositorio mensagens,
                                                         UnidadeDeTrabalho unidadeDeTrabalho) {
        return new GerenciarDestinatariosUseCase(destinatarios, mensagens, unidadeDeTrabalho);
    }

    @Bean
    GerenciarAdministradoresUseCase gerenciarAdministradores(AdministradorRepositorio administradores,
                                                             CodificadorSenha codificador,
                                                             UnidadeDeTrabalho unidadeDeTrabalho) {
        return new GerenciarAdministradoresUseCase(administradores, codificador, unidadeDeTrabalho);
    }

    @Bean
    ConsultarConversasUseCase consultarConversas(ConversaRepositorio conversas, DestinatarioRepositorio destinatarios) {
        return new ConsultarConversasUseCase(conversas, destinatarios);
    }

    @Bean
    EnviarMensagemUseCase enviarMensagem(ClienteRepositorio clientes, DestinatarioRepositorio destinatarios,
                                         ConversaRepositorio conversas, MensagemRepositorio mensagens,
                                         TransacaoRepositorio transacoes, FilaMensagensEmMemoria fila,
                                         UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio,
                                         ArChatPropriedades propriedades, RegistroDeAtendimento atendimentos) {
        return new EnviarMensagemUseCase(clientes, destinatarios, conversas, mensagens, transacoes, fila,
                unidadeDeTrabalho, relogio, propriedades.fila().tempoEstimadoPorMensagem(), atendimentos);
    }

    @Bean
    ProcessarMensagemUseCase processarMensagem(MensagemRepositorio mensagens, ConversaRepositorio conversas,
                                               DestinatarioRepositorio destinatarios, ClienteRepositorio clientes, TransacaoRepositorio transacoes,
                                               FilaMensagensEmMemoria fila, EnvioMensagemGateway envio,
                                               MetricasFila metricas, UnidadeDeTrabalho unidadeDeTrabalho,
                                               Clock relogio, ArChatPropriedades propriedades) {
        return new ProcessarMensagemUseCase(mensagens, conversas, destinatarios, clientes, transacoes, fila, envio, metricas,
                unidadeDeTrabalho, relogio, propriedades.fila().maximoTentativas());
    }

    @Bean
    ConsultarMensagensUseCase consultarMensagens(MensagemRepositorio mensagens, ConversaRepositorio conversas) {
        return new ConsultarMensagensUseCase(mensagens, conversas);
    }

    @Bean
    ConfirmarRecebimentoUseCase confirmarRecebimento(MensagemRepositorio mensagens,
                                                     UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        return new ConfirmarRecebimentoUseCase(mensagens, unidadeDeTrabalho, relogio);
    }

    @Bean
    RecuperarFilaUseCase recuperarFila(MensagemRepositorio mensagens, FilaMensagensEmMemoria fila,
                                       UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        return new RecuperarFilaUseCase(mensagens, fila, unidadeDeTrabalho, relogio);
    }

    @Bean
    ZoneId fusoDosRelatorios() {
        return ZoneId.of("America/Sao_Paulo");
    }

    @Bean
    RelatorioAtendimentosUseCase relatorioAtendimentos(AtendimentoRepositorio atendimentos, ConversaRepositorio conversas,
                                                       DestinatarioRepositorio destinatarios, ZoneId fuso) {
        return new RelatorioAtendimentosUseCase(atendimentos, conversas, destinatarios, fuso);
    }

    @Bean
    RegistroDeAtendimento registroDeAtendimento(AtendimentoRepositorio atendimentos) {
        return new RegistroDeAtendimento(atendimentos);
    }

    @Bean
    GerenciarAtendimentoUseCase gerenciarAtendimento(ConversaRepositorio conversas, AtendimentoRepositorio atendimentos,
                                                     UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio,
                                                     MensagemRepositorio mensagens, DestinatarioRepositorio destinatarios,
                                                     ConexaoWhatsApp canal, OperadorRepositorio operadores) {
        return new GerenciarAtendimentoUseCase(conversas, atendimentos, unidadeDeTrabalho, relogio, mensagens,
                destinatarios, canal, operadores);
    }

    @Bean
    ReceberMensagemUseCase receberMensagem(DestinatarioRepositorio destinatarios, ConversaRepositorio conversas,
                                           MensagemRepositorio mensagens, RegistroDeAtendimento atendimentos,
                                           UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        return new ReceberMensagemUseCase(destinatarios, conversas, mensagens, atendimentos, unidadeDeTrabalho, relogio);
    }

    @Bean
    AtualizarStatusPeloCanalUseCase atualizarStatusPeloCanal(MensagemRepositorio mensagens,
                                                             UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        return new AtualizarStatusPeloCanalUseCase(mensagens, unidadeDeTrabalho, relogio);
    }

    @Bean
    ListarCanaisUseCase listarCanais(ConexaoWhatsApp conexao) {
        return new ListarCanaisUseCase(conexao);
    }

    @Bean
    RelatorioFaturamentoUseCase relatorioFaturamento(ClienteRepositorio clientes, TransacaoRepositorio transacoes,
                                                     MensagemRepositorio mensagens) {
        return new RelatorioFaturamentoUseCase(clientes, transacoes, mensagens);
    }

    @Bean
    RelatorioCampanhasUseCase relatorioCampanhas(CampanhaRepositorio campanhas, MensagemRepositorio mensagens) {
        return new RelatorioCampanhasUseCase(campanhas, mensagens);
    }

    @Bean
    MonitorarFilaUseCase monitorarFila(FilaMensagensEmMemoria fila, MetricasFila metricas,
                                       MensagemRepositorio mensagens, Clock relogio) {
        return new MonitorarFilaUseCase(fila, metricas, mensagens, relogio);
    }

    @Bean
    GerenciarConexaoWhatsAppUseCase gerenciarConexaoWhatsApp(ConexaoWhatsApp conexao) {
        return new GerenciarConexaoWhatsAppUseCase(conexao);
    }

    @Bean
    DispararCampanhaUseCase dispararCampanha(ClienteRepositorio clientes, DestinatarioRepositorio destinatarios,
                                             ConversaRepositorio conversas, MensagemRepositorio mensagens,
                                             TransacaoRepositorio transacoes, CampanhaRepositorio campanhas,
                                             FilaMensagensEmMemoria fila, UnidadeDeTrabalho unidadeDeTrabalho,
                                             Clock relogio) {
        return new DispararCampanhaUseCase(clientes, destinatarios, conversas, mensagens, transacoes, campanhas, fila,
                unidadeDeTrabalho, relogio);
    }

    @Bean
    ConsultarCampanhasUseCase consultarCampanhas(CampanhaRepositorio campanhas, MensagemRepositorio mensagens) {
        return new ConsultarCampanhasUseCase(campanhas, mensagens);
    }
}
