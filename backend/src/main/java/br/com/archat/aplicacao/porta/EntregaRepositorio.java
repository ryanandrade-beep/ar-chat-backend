package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.entrega.ConfiguracaoDeEntrega;
import br.com.archat.dominio.entrega.Entrega;
import br.com.archat.dominio.entrega.StatusEntrega;

import java.time.Instant;
import br.com.archat.dominio.entrega.ItemDeEntrega;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface EntregaRepositorio {
    void salvar(Entrega entrega);

    void vincularMensagem(UUID entregaId, UUID mensagemId);

    Map<UUID, List<ItemDeEntrega>> itensPorMensagem(Collection<UUID> mensagemIds);

    Optional<Entrega> buscarPorId(UUID id);

    List<Entrega> listar(UUID clienteId, StatusEntrega status, Instant de, Instant ate, String busca);

    Optional<Entrega> abertaPorFinalDoTelefone(UUID clienteId, String ultimosDigitos);

    Optional<ConfiguracaoDeEntrega> buscarConfiguracao(UUID clienteId);

    void salvar(ConfiguracaoDeEntrega configuracao);
}
