package br.com.archat.aplicacao.departamento;

import br.com.archat.aplicacao.porta.DepartamentoRepositorio;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.atendimento.OrigemAtendimento;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.departamento.ConfiguracaoUra;
import br.com.archat.dominio.departamento.Departamento;
import br.com.archat.dominio.mensagem.Mensagem;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * URA: no início de um atendimento aberto pelo cliente final, envia boas-vindas e o menu de departamentos;
 * depois interpreta a resposta (número ou nome). Respostas inválidas repetem o menu até o limite configurado;
 * esgotado o limite, a conversa vai para o departamento padrão. As mensagens da URA ficam no histórico como
 * automáticas e não são cobradas. Quem chama envia os textos devolvidos pelo canal.
 */
public class AtendimentoAutomatico {

    private final DepartamentoRepositorio departamentos;
    private final MensagemRepositorio mensagens;

    public AtendimentoAutomatico(DepartamentoRepositorio departamentos, MensagemRepositorio mensagens) {
        this.departamentos = departamentos;
        this.mensagens = mensagens;
    }

    public List<String> aoReceber(Conversa conversa, Destinatario destinatario, Atendimento atendimento,
                                  Mensagem recebida, Instant agora) {
        ConfiguracaoUra ura = departamentos.buscarConfiguracao(conversa.clienteId()).orElse(null);
        if (ura == null || !ura.ativa() || (destinatario.grupo() && ura.ignorarGrupos())) {
            return List.of();
        }
        List<Departamento> opcoes = departamentos.listarPorCliente(conversa.clienteId()).stream()
                .filter(Departamento::ativo)
                .sorted(Comparator.comparing(Departamento::nome, String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (opcoes.isEmpty()) {
            return List.of();
        }
        List<String> respostas = new ArrayList<>();
        boolean novoAtendimento = atendimento.origem() == OrigemAtendimento.CLIENTE_FINAL
                && atendimento.mensagensRecebidas() == 1 && atendimento.mensagensEnviadas() == 0;
        if (novoAtendimento) {
            conversa.iniciarUra();
            respostas.add(ura.boasVindas(destinatario.nome()));
            respostas.add(ura.menu(opcoes));
        } else if (conversa.aguardandoUra()) {
            var escolhido = ConfiguracaoUra.escolha(recebida.conteudo(), opcoes);
            if (escolhido.isPresent()) {
                conversa.definirDepartamento(escolhido.get().id());
                respostas.add("Certo! Você será atendido pelo departamento " + escolhido.get().nome()
                        + ". Aguarde um momento, por favor.");
            } else if (conversa.registrarTentativaInvalida() >= ura.maximoTentativas()) {
                conversa.definirDepartamento(ura.departamentoPadraoId());
                respostas.add("Vamos te encaminhar para um de nossos atendentes. Aguarde um momento, por favor.");
            } else {
                respostas.add("Não entendi a opção. " + ura.menu(opcoes));
            }
        }
        for (String texto : respostas) {
            Mensagem automatica = Mensagem.automatica(conversa.id(), texto, agora);
            mensagens.salvar(automatica);
            conversa.registrar(automatica);
        }
        return respostas;
    }
}
