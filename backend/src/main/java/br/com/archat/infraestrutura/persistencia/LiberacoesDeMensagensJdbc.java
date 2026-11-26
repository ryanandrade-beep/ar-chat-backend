package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.LiberacoesDeMensagens;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
class LiberacoesDeMensagensJdbc implements LiberacoesDeMensagens {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    LiberacoesDeMensagensJdbc(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public void agendar(UUID mensagemId, Instant liberarEm) {
        entityManager.flush();
        jdbc.update("INSERT INTO liberacao_pendente (mensagem_id, liberar_em) VALUES (?, ?)", mensagemId,
                Timestamp.from(liberarEm));
    }

    @Override
    public List<UUID> vencidas(Instant agora, int limite) {
        return jdbc.queryForList("SELECT mensagem_id FROM liberacao_pendente WHERE liberar_em <= ? ORDER BY liberar_em LIMIT ?",
                UUID.class, Timestamp.from(agora), limite);
    }

    @Override
    public void remover(UUID mensagemId) {
        jdbc.update("DELETE FROM liberacao_pendente WHERE mensagem_id = ?", mensagemId);
    }

    @Override
    public boolean existe(UUID mensagemId) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM liberacao_pendente WHERE mensagem_id = ?", Integer.class,
                mensagemId);
        return total != null && total > 0;
    }
}
