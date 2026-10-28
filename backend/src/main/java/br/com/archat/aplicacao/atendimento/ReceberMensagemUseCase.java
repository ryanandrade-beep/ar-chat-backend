package br.com.archat.aplicacao.atendimento;

import br.com.archat.aplicacao.departamento.AtendimentoAutomatico;
import br.com.archat.aplicacao.porta.ConexaoWhatsApp;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.excecao.RecursoNaoEncontradoException;
import br.com.archat.dominio.mensagem.ContatoCompartilhado;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Midia;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Registra uma mensagem escrita pelo cliente final: grava na conversa, conta como não lida e abre ou
 * atualiza o atendimento (status "aguardando"). Não tem custo e não passa pela fila.
 */
public class ReceberMensagemUseCase {

    private final DestinatarioRepositorio destinatarios;
    private final ConversaRepositorio conversas;
    private final MensagemRepositorio mensagens;
    private final RegistroDeAtendimento atendimentos;
    private final UnidadeDeTrabalho unidadeDeTrabalho;
    private final Clock relogio;
    private final AtendimentoAutomatico ura;
    private final ConexaoWhatsApp canal;

    public ReceberMensagemUseCase(DestinatarioRepositorio destinatarios, ConversaRepositorio conversas,
                                  MensagemRepositorio mensagens, RegistroDeAtendimento atendimentos,
                                  UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio) {
        this(destinatarios, conversas, mensagens, atendimentos, unidadeDeTrabalho, relogio, null, null);
    }

    public ReceberMensagemUseCase(DestinatarioRepositorio destinatarios, ConversaRepositorio conversas,
                                  MensagemRepositorio mensagens, RegistroDeAtendimento atendimentos,
                                  UnidadeDeTrabalho unidadeDeTrabalho, Clock relogio, AtendimentoAutomatico ura,
                                  ConexaoWhatsApp canal) {
        this.ura = ura;
        this.canal = canal;
        this.destinatarios = destinatarios;
        this.conversas = conversas;
        this.mensagens = mensagens;
        this.atendimentos = atendimentos;
        this.unidadeDeTrabalho = unidadeDeTrabalho;
        this.relogio = relogio;
    }

    public Mensagem simularResposta(UUID clienteId, UUID conversaId, String conteudo) {
        return unidadeDeTrabalho.executar(() -> {
            Conversa conversa = conversas.buscarPorId(conversaId)
                    .filter(c -> c.pertenceA(clienteId))
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Conversa"));
            return registrar(conversa, conteudo, null, null, relogio.instant()).mensagem();
        });
    }

    public Optional<Mensagem> doCanal(String telefoneOuGrupo, String conteudo, String idExterno, Instant quando) {
        return doCanal(telefoneOuGrupo, conteudo, null, idExterno, quando);
    }

    public Optional<Mensagem> doCanal(String telefoneOuGrupo, String conteudo, Midia midia, String idExterno,
                                      Instant quando) {
        return doCanal(null, telefoneOuGrupo, conteudo, midia, idExterno, quando);
    }

    public Optional<Mensagem> doCanal(UUID clienteId, String telefoneOuGrupo, String conteudo, Midia midia,
                                      String idExterno, Instant quando) {
        if (idExterno != null && mensagens.buscarPorIdExterno(idExterno).isPresent()) {
            return Optional.empty();
        }
        Optional<Registro> registro = unidadeDeTrabalho.executar(() -> conversaDoRemetente(clienteId, telefoneOuGrupo)
                .map(conversa -> registrar(conversa, conteudo, midia, idExterno,
                        quando == null ? relogio.instant() : quando)));
        registro.ifPresent(r -> enviarRespostasDaUra(r.clienteId(), r.telefone(), r.respostasDaUra()));
        return registro.map(Registro::mensagem);
    }

    public Optional<Mensagem> contatoDoCanal(UUID clienteId, String telefoneOuGrupo, ContatoCompartilhado contato,
                                             String idExterno, Instant quando) {
        if (idExterno != null && mensagens.buscarPorIdExterno(idExterno).isPresent()) {
            return Optional.empty();
        }
        Instant momento = quando == null ? relogio.instant() : quando;
        Optional<Registro> registro = unidadeDeTrabalho.executar(() -> conversaDoRemetente(clienteId, telefoneOuGrupo)
                .map(conversa -> registrarMensagem(conversa, Mensagem.contatoRecebido(conversa.id(), contato, idExterno, momento),
                        momento)));
        registro.ifPresent(r -> enviarRespostasDaUra(r.clienteId(), r.telefone(), r.respostasDaUra()));
        return registro.map(Registro::mensagem);
    }

    private Optional<Conversa> conversaDoRemetente(UUID clienteId, String telefoneOuGrupo) {
        List<Destinatario> candidatos = destinatarios.buscarPorTelefones(variacoes(telefoneOuGrupo)).stream()
                .filter(d -> clienteId == null || d.clienteId().equals(clienteId))
                .toList();
        return candidatos.stream()
                .map(d -> conversas.buscarPorDestinatario(d.id()).orElseGet(() -> Conversa.iniciar(d.clienteId(), d.id())))
                .max(Comparator.comparing(c -> c.dataUltimaMensagem() == null ? Instant.EPOCH : c.dataUltimaMensagem()));
    }

    private static Set<String> variacoes(String telefoneOuGrupo) {
        String valor = telefoneOuGrupo == null ? "" : telefoneOuGrupo.strip().toLowerCase();
        Set<String> variacoes = new LinkedHashSet<>();
        variacoes.add(valor);
        if (!valor.contains("-") && valor.startsWith("55") && valor.length() > 11) {
            variacoes.add(valor.substring(2));
        }
        return variacoes;
    }

    private Registro registrar(Conversa conversa, String conteudo, Midia midia, String idExterno, Instant quando) {
        return registrarMensagem(conversa, Mensagem.recebidaDoDestinatario(conversa.id(), conteudo, midia, idExterno, quando),
                quando);
    }

    private Registro registrarMensagem(Conversa conversa, Mensagem mensagem, Instant quando) {
        conversa.registrarRecebida(mensagem);
        conversas.salvar(conversa);
        mensagens.salvar(mensagem);
        Atendimento atendimento = atendimentos.aoReceber(conversa, quando);
        Destinatario destinatario = destinatarios.buscarPorId(conversa.destinatarioId()).orElse(null);
        List<String> respostas = ura == null || destinatario == null ? List.of()
                : ura.aoReceber(conversa, destinatario, atendimento, mensagem, quando);
        conversas.salvar(conversa);
        return new Registro(mensagem, conversa.clienteId(), destinatario == null ? null : destinatario.telefone(), respostas);
    }

    private void enviarRespostasDaUra(UUID clienteId, String telefone, List<String> respostas) {
        if (canal == null || telefone == null) {
            return;
        }
        for (String texto : respostas) {
            try {
                canal.enviarAvisoDoSistema(clienteId, telefone, texto);
            } catch (RuntimeException e) {
                return;
            }
        }
    }

    private record Registro(Mensagem mensagem, UUID clienteId, String telefone, List<String> respostasDaUra) {
    }
}
