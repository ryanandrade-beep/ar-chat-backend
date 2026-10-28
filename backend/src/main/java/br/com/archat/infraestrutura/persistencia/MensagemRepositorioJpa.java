package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.FiltroMensagens;
import br.com.archat.aplicacao.porta.MensagemRepositorio;
import br.com.archat.dominio.mensagem.Mensagem;
import br.com.archat.dominio.mensagem.Prioridade;
import br.com.archat.dominio.mensagem.StatusMensagem;
import br.com.archat.infraestrutura.persistencia.entidade.ConversaEntity;
import br.com.archat.infraestrutura.persistencia.entidade.MensagemEntity;
import br.com.archat.infraestrutura.persistencia.jpa.MensagemJpa;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
class MensagemRepositorioJpa implements MensagemRepositorio {

    private static final List<StatusMensagem> PENDENTES = List.of(StatusMensagem.ENFILEIRADA, StatusMensagem.PROCESSANDO);

    private final MensagemJpa jpa;

    MensagemRepositorioJpa(MensagemJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Mensagem m) {
        MensagemEntity entidade = new MensagemEntity(m.id(), m.conversaId(), m.campanhaId(), m.remetente(), m.conteudo(),
                m.prioridade(), m.status(), m.custo(), m.tentativas(), m.idExterno(), m.criadaEm(), m.atualizadaEm());
        entidade.definirMidia(m.midia());
        entidade.definirAutomatica(m.automatica());
        entidade.definirContato(m.contato());
        entidade.definirAutoria(m.operadorId(), m.interna());
        jpa.save(entidade);
    }

    @Override
    public Optional<Mensagem> buscarPorId(UUID id) {
        return jpa.findById(id).map(MensagemRepositorioJpa::paraDominio);
    }

    @Override
    public Optional<Mensagem> buscarDoCliente(UUID mensagemId, UUID clienteId) {
        return jpa.buscarDoCliente(mensagemId, clienteId).map(MensagemRepositorioJpa::paraDominio);
    }

    @Override
    public Optional<Mensagem> buscarPorIdExterno(String idExterno) {
        return idExterno == null ? Optional.empty() : jpa.findByIdExterno(idExterno).map(MensagemRepositorioJpa::paraDominio);
    }

    @Override
    public List<Mensagem> listar(FiltroMensagens filtro) {
        return jpa.findAll(especificacao(filtro), Sort.by("criadaEm")).stream()
                .map(MensagemRepositorioJpa::paraDominio).toList();
    }

    @Override
    public List<Mensagem> listarPendentes() {
        return jpa.findByStatusInOrderByCriadaEmAsc(PENDENTES).stream().map(MensagemRepositorioJpa::paraDominio).toList();
    }

    @Override
    public boolean existeParaDestinatario(UUID destinatarioId) {
        return jpa.existeParaDestinatario(destinatarioId);
    }

    @Override
    public Map<StatusMensagem, Long> contarPorStatus() {
        return contagem(jpa.contarPorStatus());
    }

    @Override
    public Map<StatusMensagem, Long> contarPorStatusDaCampanha(UUID campanhaId) {
        return contagem(jpa.contarPorStatusDaCampanha(campanhaId));
    }

    private static Map<StatusMensagem, Long> contagem(List<Object[]> linhas) {
        Map<StatusMensagem, Long> contagem = new EnumMap<>(StatusMensagem.class);
        for (StatusMensagem status : StatusMensagem.values()) {
            contagem.put(status, 0L);
        }
        linhas.forEach(linha -> contagem.put((StatusMensagem) linha[0], (Long) linha[1]));
        return contagem;
    }

    @Override
    public List<ContagemPorCliente> contarPorCliente(Instant de, Instant ate) {
        return jpa.contarPorCliente(de, ate).stream()
                .map(l -> new ContagemPorCliente((UUID) l[0], (Prioridade) l[1], (StatusMensagem) l[2], (Long) l[3]))
                .toList();
    }

    @Override
    public Map<UUID, Map<StatusMensagem, Long>> contarPorStatusDasCampanhas(Collection<UUID> campanhaIds) {
        Map<UUID, Map<StatusMensagem, Long>> resultado = new HashMap<>();
        if (campanhaIds.isEmpty()) {
            return resultado;
        }
        jpa.contarPorStatusDasCampanhas(campanhaIds).forEach(l -> resultado
                .computeIfAbsent((UUID) l[0], id -> new EnumMap<>(StatusMensagem.class))
                .put((StatusMensagem) l[1], (Long) l[2]));
        return resultado;
    }

    private static Specification<MensagemEntity> especificacao(FiltroMensagens filtro) {
        return (raiz, consulta, cb) -> {
            List<Predicate> condicoes = new ArrayList<>();
            Subquery<UUID> conversasDoCliente = consulta.subquery(UUID.class);
            var conversa = conversasDoCliente.from(ConversaEntity.class);
            conversasDoCliente.select(conversa.get("id")).where(cb.equal(conversa.get("clienteId"), filtro.clienteId()));
            condicoes.add(raiz.get("conversaId").in(conversasDoCliente));
            if (filtro.conversaId() != null) {
                condicoes.add(cb.equal(raiz.get("conversaId"), filtro.conversaId()));
            }
            if (filtro.status() != null) {
                condicoes.add(cb.equal(raiz.get("status"), filtro.status()));
            }
            if (filtro.prioridade() != null) {
                condicoes.add(cb.equal(raiz.get("prioridade"), filtro.prioridade()));
            }
            return cb.and(condicoes.toArray(Predicate[]::new));
        };
    }

    private static Mensagem paraDominio(MensagemEntity e) {
        return Mensagem.reconstituir(e.getId(), e.getConversaId(), e.getCampanhaId(), e.getRemetente(), e.getConteudo(), e.getMidia(), e.getPrioridade(),
                e.getStatus(), e.getCusto(), e.getTentativas(), e.getIdExterno(), e.getCriadaEm(), e.getAtualizadaEm(),
                e.isAutomatica()).comContato(e.getContato()).comAutoria(e.getOperadorId(), e.isInterna());
    }
}
