package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.EtiquetasDosContatos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Repository
class EtiquetasDosContatosJdbc implements EtiquetasDosContatos {

    private final JdbcTemplate jdbc;

    EtiquetasDosContatosJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<UUID, List<String>> doCliente(UUID clienteId) {
        Map<UUID, List<String>> porContato = new LinkedHashMap<>();
        jdbc.query("""
                SELECT e.destinatario_id, e.etiqueta FROM destinatario_etiqueta e
                JOIN destinatario d ON d.id = e.destinatario_id
                WHERE d.cliente_id = ? ORDER BY e.etiqueta""", rs -> {
            porContato.computeIfAbsent(rs.getObject(1, UUID.class), k -> new ArrayList<>()).add(rs.getString(2));
        }, clienteId);
        return porContato;
    }

    @Override
    public void definir(UUID destinatarioId, List<String> etiquetas) {
        jdbc.update("DELETE FROM destinatario_etiqueta WHERE destinatario_id = ?", destinatarioId);
        for (String etiqueta : etiquetas) {
            jdbc.update("INSERT INTO destinatario_etiqueta (destinatario_id, etiqueta) VALUES (?, ?)",
                    destinatarioId, etiqueta);
        }
    }

    @Override
    public Set<UUID> comEtiqueta(UUID clienteId, String etiqueta) {
        return new HashSet<>(jdbc.queryForList("""
                SELECT e.destinatario_id FROM destinatario_etiqueta e
                JOIN destinatario d ON d.id = e.destinatario_id
                WHERE d.cliente_id = ? AND e.etiqueta = ?""", UUID.class, clienteId, etiqueta));
    }
}
