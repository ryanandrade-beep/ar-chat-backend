package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.ParticipanteRepositorio;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Tabela de ligação simples (conversa × operador), acessada direto por SQL. */
@Repository
class ParticipanteRepositorioJdbc implements ParticipanteRepositorio {

    private final JdbcClient jdbc;

    ParticipanteRepositorioJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<UUID> participantes(UUID conversaId) {
        return new HashSet<>(jdbc.sql("SELECT operador_id FROM conversa_participante WHERE conversa_id = ?")
                .param(conversaId).query(UUID.class).list());
    }

    @Override
    public void adicionar(UUID conversaId, UUID operadorId) {
        if (!participantes(conversaId).contains(operadorId)) {
            jdbc.sql("INSERT INTO conversa_participante (conversa_id, operador_id, desde) VALUES (?, ?, ?)")
                    .params(conversaId, operadorId, Timestamp.from(Instant.now())).update();
        }
    }

    @Override
    public void remover(UUID conversaId, UUID operadorId) {
        jdbc.sql("DELETE FROM conversa_participante WHERE conversa_id = ? AND operador_id = ?")
                .params(conversaId, operadorId).update();
    }
}
