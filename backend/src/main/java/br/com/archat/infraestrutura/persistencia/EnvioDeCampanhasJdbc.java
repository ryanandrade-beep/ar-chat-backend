package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.EnvioDeCampanhas;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Repository
class EnvioDeCampanhasJdbc implements EnvioDeCampanhas {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    EnvioDeCampanhasJdbc(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public void salvar(Envio envio) {
        entityManager.flush();
        jdbc.update("""
                INSERT INTO campanha_envio (campanha_id, agendada_para, intervalo_segundos, etiqueta)
                VALUES (?, ?, ?, ?)""", envio.campanhaId(),
                envio.agendadaPara() == null ? null : Timestamp.from(envio.agendadaPara()),
                envio.intervaloSegundos(), envio.etiqueta());
    }

    @Override
    public Map<UUID, Envio> buscar(Collection<UUID> campanhaIds) {
        Map<UUID, Envio> resultado = new HashMap<>();
        for (UUID id : campanhaIds) {
            jdbc.query("SELECT agendada_para, intervalo_segundos, etiqueta FROM campanha_envio WHERE campanha_id = ?",
                    rs -> {
                        Timestamp quando = rs.getTimestamp(1);
                        resultado.put(id, new Envio(id, quando == null ? null : quando.toInstant(), rs.getInt(2),
                                rs.getString(3)));
                    }, id);
        }
        return resultado;
    }
}
