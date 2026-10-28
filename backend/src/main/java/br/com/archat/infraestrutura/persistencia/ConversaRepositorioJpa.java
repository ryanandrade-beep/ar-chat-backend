package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.ConversaRepositorio;
import br.com.archat.dominio.conversa.Conversa;
import br.com.archat.infraestrutura.persistencia.entidade.ConversaEntity;
import br.com.archat.infraestrutura.persistencia.jpa.ConversaJpa;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
class ConversaRepositorioJpa implements ConversaRepositorio {

    private final ConversaJpa jpa;

    ConversaRepositorioJpa(ConversaJpa jpa) {
        this.jpa = jpa;
    }

    @Override
    public void salvar(Conversa c) {
        ConversaEntity entidade = new ConversaEntity(c.id(), c.clienteId(), c.destinatarioId(), c.ultimaMensagem(),
                c.dataUltimaMensagem(), c.naoLidas(), c.statusAtendimento());
        entidade.definirOperador(c.operadorId());
        entidade.definirUra(c.departamentoId(), c.aguardandoUra(), c.tentativasUra());
        jpa.save(entidade);
    }

    @Override
    public Optional<Conversa> buscarPorId(UUID id) {
        return jpa.findById(id).map(ConversaRepositorioJpa::paraDominio);
    }

    @Override
    public Optional<Conversa> buscarPorDestinatario(UUID destinatarioId) {
        return jpa.findByDestinatarioId(destinatarioId).map(ConversaRepositorioJpa::paraDominio);
    }

    @Override
    public List<Conversa> listarPorCliente(UUID clienteId) {
        return jpa.findByClienteIdOrderByDataUltimaMensagemDesc(clienteId).stream()
                .map(ConversaRepositorioJpa::paraDominio).toList();
    }

    private static Conversa paraDominio(ConversaEntity e) {
        return Conversa.reconstituir(e.getId(), e.getClienteId(), e.getDestinatarioId(), e.getUltimaMensagem(),
                e.getDataUltimaMensagem(), e.getNaoLidas(), e.getStatusAtendimento(), e.getOperadorId(),
                e.getDepartamentoId(), e.isAguardandoUra(), e.getTentativasUra());
    }
}
