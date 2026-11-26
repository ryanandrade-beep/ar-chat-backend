package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.DepartamentosDosOperadores;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
class DepartamentosDosOperadoresJdbc implements DepartamentosDosOperadores {

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    DepartamentosDosOperadoresJdbc(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public Map<UUID, List<UUID>> doCliente(UUID clienteId) {
        Map<UUID, List<UUID>> porOperador = new LinkedHashMap<>();
        jdbc.query("""
                SELECT od.operador_id, od.departamento_id FROM operador_departamento od
                JOIN operador o ON o.id = od.operador_id
                WHERE o.cliente_id = ?""", rs -> {
            porOperador.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getObject(2, UUID.class));
        }, clienteId);
        return porOperador;
    }

    @Override
    public void definirOperadoresDoDepartamento(UUID departamentoId, List<UUID> operadorIds) {
        entityManager.flush();
        jdbc.update("DELETE FROM operador_departamento WHERE departamento_id = ?", departamentoId);
        for (UUID operadorId : operadorIds) {
            jdbc.update("INSERT INTO operador_departamento (operador_id, departamento_id) VALUES (?, ?)", operadorId,
                    departamentoId);
        }
    }

    @Override
    public void definir(UUID operadorId, List<UUID> departamentoIds) {
        entityManager.flush();
        jdbc.update("DELETE FROM operador_departamento WHERE operador_id = ?", operadorId);
        for (UUID departamentoId : departamentoIds) {
            jdbc.update("INSERT INTO operador_departamento (operador_id, departamento_id) VALUES (?, ?)", operadorId,
                    departamentoId);
        }
    }
}
