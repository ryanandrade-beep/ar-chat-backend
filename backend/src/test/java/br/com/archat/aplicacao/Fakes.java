package br.com.archat.aplicacao;

import br.com.archat.aplicacao.porta.AtendimentoRepositorio;
import br.com.archat.aplicacao.porta.CampanhaRepositorio;
import br.com.archat.aplicacao.porta.ClienteRepositorio;
import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.aplicacao.porta.DestinatarioRepositorio;
import br.com.archat.aplicacao.porta.EnvioMensagemGateway;
import br.com.archat.aplicacao.porta.FilaMensagens;
import br.com.archat.aplicacao.porta.FiltroMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.aplicacao.porta.MetricasFila;
import br.com.archat.aplicacao.porta.TransacaoRepositorio;
import br.com.archat.aplicacao.porta.UnidadeDeTrabalho;
import br.com.archat.dominio.atendimento.Atendimento;
import br.com.archat.dominio.campanha.Campanha;
import br.com.archat.dominio.cliente.Cliente;
import br.com.archat.dominio.cliente.Documento;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.dominio.conversa.Destinatario;
import br.com.archat.dominio.financeiro.Transacao;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Prioridade;
import br.com.archat.dominio.mensagem.StatusMensagem;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Implementações em memória das portas, para testar os casos de uso sem banco nem Spring. */
final class Fakes {

    private Fakes() {
    }

    static final UnidadeDeTrabalho SEM_TRANSACAO = new UnidadeDeTrabalho() {
        @Override
        public <T> T executar(Supplier<T> bloco) {
            return bloco.get();
        }
    };

    static class Clientes implements ClienteRepositorio {
        final Map<UUID, Cliente> dados = new HashMap<>();
        public void salvar(Cliente c) { dados.put(c.id(), c); }
        public Optional<Cliente> buscarPorId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public Optional<Cliente> buscarPorIdParaAtualizacao(UUID id) { return buscarPorId(id); }
        public Optional<Cliente> buscarPorDocumento(Documento d) {
            return dados.values().stream().filter(c -> c.documento().equals(d)).findFirst();
        }
        public boolean existeComDocumento(Documento d) { return buscarPorDocumento(d).isPresent(); }
        public List<Cliente> listarTodos() { return List.copyOf(dados.values()); }
        public int zerarConsumoMensalDosPosPagos() { return 0; }
    }

    static class Destinatarios implements DestinatarioRepositorio {
        final Map<UUID, Destinatario> dados = new LinkedHashMap<>();
        public void salvar(Destinatario d) { dados.put(d.id(), d); }
        public Optional<Destinatario> buscarPorId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public List<Destinatario> buscarPorIds(Collection<UUID> ids) { return ids.stream().map(dados::get).toList(); }
        public List<Destinatario> listarPorCliente(UUID clienteId) {
            return dados.values().stream().filter(d -> d.pertenceA(clienteId)).toList();
        }
        public Optional<Destinatario> buscarPorClienteETelefone(UUID clienteId, String telefone) {
            return listarPorCliente(clienteId).stream().filter(d -> d.telefone().equals(telefone)).findFirst();
        }
        public List<Destinatario> buscarPorTelefones(Collection<String> telefones) {
            return dados.values().stream().filter(d -> telefones.contains(d.telefone())).toList();
        }
        public List<Destinatario> listarQueAceitamOfertas(UUID clienteId) {
            return listarPorCliente(clienteId).stream().filter(Destinatario::aceitaOfertas).toList();
        }
        public void excluir(UUID id) { dados.remove(id); }
    }

    static class Campanhas implements CampanhaRepositorio {
        final Map<UUID, Campanha> dados = new LinkedHashMap<>();
        public void salvar(Campanha c) { dados.put(c.id(), c); }
        public Optional<Campanha> buscarPorId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public List<Campanha> listarPorCliente(UUID clienteId) {
            return dados.values().stream().filter(c -> c.pertenceA(clienteId)).toList();
        }
        public List<Campanha> listarPorPeriodo(UUID clienteId, Instant de, Instant ate) {
            return dados.values().stream()
                    .filter(c -> clienteId == null || c.pertenceA(clienteId))
                    .filter(c -> !c.criadaEm().isBefore(de) && c.criadaEm().isBefore(ate))
                    .toList();
        }
    }

    static class Atendimentos implements AtendimentoRepositorio {
        final Map<UUID, Atendimento> dados = new LinkedHashMap<>();
        public void salvar(Atendimento a) { dados.put(a.id(), a); }
        public Optional<Atendimento> buscarAbertoDaConversa(UUID conversaId) {
            return dados.values().stream().filter(a -> a.conversaId().equals(conversaId) && a.status().aberto()).findFirst();
        }
        public List<Atendimento> listarFinalizados(UUID clienteId, Instant de, Instant ate) {
            return dados.values().stream()
                    .filter(a -> !a.status().aberto() && (clienteId == null || a.clienteId().equals(clienteId)))
                    .filter(a -> !a.finalizadoEm().isBefore(de) && a.finalizadoEm().isBefore(ate))
                    .toList();
        }
        public List<Atendimento> listarAbertosNoPeriodo(UUID clienteId, Instant de, Instant ate) {
            return dados.values().stream()
                    .filter(a -> a.clienteId().equals(clienteId) && !a.abertoEm().isBefore(de) && a.abertoEm().isBefore(ate))
                    .toList();
        }
    }

    static class Conversas implements ConversaRepositorio {
        final Map<UUID, Conversa> dados = new LinkedHashMap<>();
        public void salvar(Conversa c) { dados.put(c.id(), c); }
        public Optional<Conversa> buscarPorId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public Optional<Conversa> buscarPorDestinatario(UUID id) {
            return dados.values().stream().filter(c -> c.destinatarioId().equals(id)).findFirst();
        }
        public List<Conversa> listarPorCliente(UUID clienteId) {
            return dados.values().stream().filter(c -> c.pertenceA(clienteId)).toList();
        }
    }

    static class Mensagens implements MensagemRepositorio {
        final Map<UUID, Mensagem> dados = new LinkedHashMap<>();
        public void salvar(Mensagem m) { dados.put(m.id(), m); }
        public Optional<Mensagem> buscarPorId(UUID id) { return Optional.ofNullable(dados.get(id)); }
        public Optional<Mensagem> buscarDoCliente(UUID mensagemId, UUID clienteId) { return buscarPorId(mensagemId); }
        public Optional<Mensagem> buscarPorIdExterno(String idExterno) {
            return dados.values().stream().filter(m -> idExterno.equals(m.idExterno())).findFirst();
        }
        public List<Mensagem> listar(FiltroMensagens filtro) { return List.copyOf(dados.values()); }
        public List<Mensagem> listarPendentes() {
            return dados.values().stream().filter(m -> m.status().pendente()).toList();
        }
        public boolean existeParaDestinatario(UUID destinatarioId) { return false; }
        public Map<StatusMensagem, Long> contarPorStatus() { return Map.of(); }
        public Map<StatusMensagem, Long> contarPorStatusDaCampanha(UUID campanhaId) { return Map.of(); }
        List<ContagemPorCliente> contagensPorCliente = new ArrayList<>();
        Map<UUID, Map<StatusMensagem, Long>> contagensPorCampanha = new HashMap<>();
        public List<ContagemPorCliente> contarPorCliente(Instant de, Instant ate) { return contagensPorCliente; }
        public Map<UUID, Map<StatusMensagem, Long>> contarPorStatusDasCampanhas(Collection<UUID> ids) {
            return contagensPorCampanha;
        }
    }

    static class Transacoes implements TransacaoRepositorio {
        final List<Transacao> dados = new ArrayList<>();
        public void salvar(Transacao t) { dados.add(t); }
        public List<Transacao> listar(UUID clienteId, Instant de, Instant ate) {
            return dados.stream()
                    .filter(t -> clienteId == null || t.clienteId().equals(clienteId))
                    .filter(t -> !t.dataHora().isBefore(de) && t.dataHora().isBefore(ate))
                    .toList();
        }
        public Optional<Transacao> ultimaAntesDe(UUID clienteId, Instant instante) {
            return dados.stream()
                    .filter(t -> t.clienteId().equals(clienteId) && t.dataHora().isBefore(instante))
                    .reduce((primeira, segunda) -> segunda);
        }
        public List<TotalPorCliente> totalizarPorCliente(Instant de, Instant ate) {
            return listar(null, de, ate).stream()
                    .collect(java.util.stream.Collectors.groupingBy(t -> List.of(t.clienteId(), t.tipo())))
                    .values().stream()
                    .map(g -> new TotalPorCliente(g.getFirst().clienteId(), g.getFirst().tipo(), g.size(),
                            g.stream().map(Transacao::valor).reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add)))
                    .toList();
        }
    }

    static class Fila implements FilaMensagens {
        final Deque<UUID> itens = new ArrayDeque<>();
        public void enfileirar(UUID mensagemId, Prioridade prioridade) { itens.add(mensagemId); }
        public int tamanho(Prioridade prioridade) { return itens.size(); }
    }

    static class EnvioProgramado implements EnvioMensagemGateway {
        final Deque<Boolean> respostas = new ArrayDeque<>();
        final List<Envio> recebidos = new ArrayList<>();
        EnvioProgramado responder(Boolean... sequencia) {
            respostas.addAll(List.of(sequencia));
            return this;
        }
        public ResultadoEnvio enviar(Envio envio) {
            recebidos.add(envio);
            boolean aceito = respostas.isEmpty() || respostas.poll();
            return aceito ? ResultadoEnvio.aceito("EXT-" + envio.mensagemId() + "-" + envio.tentativa()) : ResultadoEnvio.recusado();
        }
    }

    static class Metricas implements MetricasFila {
        long enviadas;
        long temporarias;
        long definitivas;
        public void registrarEnvio(Duration espera) { enviadas++; }
        public void registrarFalhaTemporaria() { temporarias++; }
        public void registrarFalhaDefinitiva() { definitivas++; }
        public Contadores contadores() { return new Contadores(enviadas, temporarias, definitivas, Duration.ZERO); }
    }
}
