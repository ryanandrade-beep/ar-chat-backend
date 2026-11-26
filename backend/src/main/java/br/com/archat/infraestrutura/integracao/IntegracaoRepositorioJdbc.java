package br.com.archat.infraestrutura.integracao;

import br.com.archat.aplicacao.porta.IntegracaoRepositorio;
import br.com.archat.dominio.integracao.ConfiguracaoDeIntegracao;
import br.com.archat.dominio.integracao.EnvioDeIntegracao;
import br.com.archat.dominio.integracao.EventoDeIntegracao;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
class IntegracaoRepositorioJdbc implements IntegracaoRepositorio {

    private static final String COLUNAS_ENVIO = "id, cliente_id, evento, payload, status, tentativas, http_status, resposta, "
            + "criado_em, enviado_em, proxima_tentativa";

    private final JdbcTemplate jdbc;

    IntegracaoRepositorioJdbc(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<ConfiguracaoDeIntegracao> buscarConfiguracao(UUID clienteId) {
        return jdbc.query("SELECT cliente_id, url, segredo, eventos, ativa, atualizada_em FROM integracao_webhook WHERE cliente_id = ?",
                (rs, n) -> new ConfiguracaoDeIntegracao(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        eventos(rs.getString(4)), rs.getBoolean(5), rs.getTimestamp(6).toInstant()), clienteId)
                .stream().findFirst();
    }

    @Override
    public void salvar(ConfiguracaoDeIntegracao c) {
        String eventos = c.eventos().stream().map(EventoDeIntegracao::codigo).sorted().collect(Collectors.joining(","));
        int atualizadas = jdbc.update("UPDATE integracao_webhook SET url = ?, segredo = ?, eventos = ?, ativa = ?, atualizada_em = ? "
                + "WHERE cliente_id = ?", c.url(), c.segredo(), eventos, c.ativa(), Timestamp.from(c.atualizadaEm()), c.clienteId());
        if (atualizadas == 0) {
            jdbc.update("INSERT INTO integracao_webhook (cliente_id, url, segredo, eventos, ativa, atualizada_em) VALUES (?,?,?,?,?,?)",
                    c.clienteId(), c.url(), c.segredo(), eventos, c.ativa(), Timestamp.from(c.atualizadaEm()));
        }
    }

    @Override
    public void salvar(EnvioDeIntegracao e) {
        int atualizadas = jdbc.update("UPDATE integracao_envio SET status = ?, tentativas = ?, http_status = ?, resposta = ?, "
                        + "enviado_em = ?, proxima_tentativa = ? WHERE id = ?", e.status().name(), e.tentativas(), e.httpStatus(),
                e.resposta(), tempo(e.enviadoEm()), tempo(e.proximaTentativa()), e.id());
        if (atualizadas == 0) {
            jdbc.update("INSERT INTO integracao_envio (" + COLUNAS_ENVIO + ") VALUES (?,?,?,?,?,?,?,?,?,?,?)", e.id(),
                    e.clienteId(), e.evento(), e.payload(), e.status().name(), e.tentativas(), e.httpStatus(), e.resposta(),
                    tempo(e.criadoEm()), tempo(e.enviadoEm()), tempo(e.proximaTentativa()));
        }
    }

    @Override
    public Optional<EnvioDeIntegracao> buscarEnvio(UUID id) {
        return jdbc.query("SELECT " + COLUNAS_ENVIO + " FROM integracao_envio WHERE id = ?", (rs, n) -> envio(rs), id)
                .stream().findFirst();
    }

    @Override
    public List<EnvioDeIntegracao> pendentes(Instant agora, int limite) {
        return jdbc.query("SELECT " + COLUNAS_ENVIO + " FROM integracao_envio WHERE status = 'PENDENTE' AND proxima_tentativa <= ? "
                + "ORDER BY criado_em LIMIT ?", (rs, n) -> envio(rs), Timestamp.from(agora), limite);
    }

    @Override
    public List<EnvioDeIntegracao> ultimos(UUID clienteId, int limite) {
        return jdbc.query("SELECT " + COLUNAS_ENVIO + " FROM integracao_envio WHERE cliente_id = ? ORDER BY criado_em DESC LIMIT ?",
                (rs, n) -> envio(rs), clienteId, limite);
    }

    private static EnvioDeIntegracao envio(ResultSet rs) throws SQLException {
        return EnvioDeIntegracao.reconstituir(rs.getObject("id", UUID.class), rs.getObject("cliente_id", UUID.class),
                rs.getString("evento"), rs.getString("payload"), rs.getTimestamp("criado_em").toInstant(),
                EnvioDeIntegracao.Status.valueOf(rs.getString("status")), rs.getInt("tentativas"),
                (Integer) rs.getObject("http_status"), rs.getString("resposta"), instante(rs.getTimestamp("enviado_em")),
                instante(rs.getTimestamp("proxima_tentativa")));
    }

    private static Set<EventoDeIntegracao> eventos(String csv) {
        Set<EventoDeIntegracao> eventos = EnumSet.noneOf(EventoDeIntegracao.class);
        Arrays.stream(csv.split(",")).map(EventoDeIntegracao::porCodigo).flatMap(Optional::stream).forEach(eventos::add);
        return eventos;
    }

    private static Timestamp tempo(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
