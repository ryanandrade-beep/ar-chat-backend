package br.com.archat.aplicacao.porta;

import br.com.archat.dominio.atendimento.Atendimento;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AtendimentoRepositorio {

    void salvar(Atendimento atendimento);

    Optional<Atendimento> buscarAbertoDaConversa(UUID conversaId);

    List<Atendimento> listarFinalizados(UUID clienteId, Instant de, Instant ate);

    List<Atendimento> listarAbertosNoPeriodo(UUID clienteId, Instant de, Instant ate);
}
