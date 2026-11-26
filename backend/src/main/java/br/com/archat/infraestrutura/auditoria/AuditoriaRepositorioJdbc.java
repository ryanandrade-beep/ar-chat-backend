package br.com.archat.infraestrutura.auditoria;

import br.com.archat.aplicacao.porta.AuditoriaRepositorio;
import br.com.archat.dominio.auditoria.RegistroDeAuditoria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Repository
class AuditoriaRepositorioJdbc implements AuditoriaRepositorio {

    private final JdbcTemplate jdbc;

    AuditoriaRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void salvar(RegistroDeAuditoria r) {
        jdbc.update("INSERT INTO auditoria (id, cliente_id, autor_id, autor_nome, acao, recurso, detalhe, criada_em) VALUES (?,?,?,?,?,?,?,?)",
                r.id(), r.clienteId(), r.autorId(), r.autorNome(), r.acao(), r.recurso(), r.detalhe(), Timestamp.from(r.criadaEm()));
    }

    @Override
    public List<RegistroDeAuditoria> listar(UUID clienteId, Instant de, Instant ate, UUID autorId, String acao, String busca,
                                            int limite) {
        StringBuilder sql = new StringBuilder("SELECT id, cliente_id, autor_id, autor_nome, acao, recurso, detalhe, criada_em "
                + "FROM auditoria WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (clienteId != null) {
            sql.append(" AND cliente_id = ?");
            args.add(clienteId);
        }
        if (de != null) {
            sql.append(" AND criada_em >= ?");
            args.add(Timestamp.from(de));
        }
        if (ate != null) {
            sql.append(" AND criada_em < ?");
            args.add(Timestamp.from(ate));
        }
        if (autorId != null) {
            sql.append(" AND autor_id = ?");
            args.add(autorId);
        }
        if (acao != null) {
            sql.append(" AND acao = ?");
            args.add(acao);
        }
        if (busca != null) {
            sql.append(" AND (LOWER(autor_nome) LIKE ? OR LOWER(COALESCE(recurso, '')) LIKE ? OR LOWER(COALESCE(detalhe, '')) LIKE ?)");
            String termo = "%" + busca.toLowerCase(Locale.ROOT).replace("%", "").replace("_", "") + "%";
            args.add(termo);
            args.add(termo);
            args.add(termo);
        }
        sql.append(" ORDER BY criada_em DESC LIMIT ?");
        args.add(limite);
        return jdbc.query(sql.toString(), (rs, n) -> new RegistroDeAuditoria(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7),
                rs.getTimestamp(8).toInstant()), args.toArray());
    }
}
