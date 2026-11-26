package br.com.archat.infraestrutura.persistencia;

import br.com.archat.aplicacao.porta.EntregaRepositorio;
import br.com.archat.dominio.entrega.ConfiguracaoDeEntrega;
import br.com.archat.dominio.entrega.Entrega;
import br.com.archat.dominio.entrega.ItemDeEntrega;
import br.com.archat.dominio.entrega.OrigemEntrega;
import br.com.archat.dominio.entrega.StatusEntrega;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Repository
class EntregaRepositorioJdbc implements EntregaRepositorio {

    private static final String COLUNAS = """
            id, cliente_id, destinatario_id, conversa_id, operador_id, nome_cliente, telefone, endereco, pedido, valor,
            pagamento, observacao, status, criada_em, saiu_em, entregue_em, cancelada_em, motivo_cancelamento,
            aviso_saida_enviado, aviso_entrega_enviado, origem, referencia_pedido, requisicao_pedido, mensagem_do_pedido""";

    private final JdbcTemplate jdbc;
    private final EntityManager entityManager;

    EntregaRepositorioJdbc(JdbcTemplate jdbc, EntityManager entityManager) {
        this.jdbc = jdbc;
        this.entityManager = entityManager;
    }

    @Override
    public void salvar(Entrega e) {
        entityManager.flush();
        int atualizadas = jdbc.update("""
                UPDATE entrega SET endereco = ?, observacao = ?, status = ?, saiu_em = ?, entregue_em = ?, cancelada_em = ?,
                       motivo_cancelamento = ?, aviso_saida_enviado = ?, aviso_entrega_enviado = ?, referencia_pedido = ?,
                       requisicao_pedido = ?, mensagem_do_pedido = ? WHERE id = ?""", e.endereco(), e.observacao(),
                e.status().name(), tempo(e.saiuEm()), tempo(e.entregueEm()), tempo(e.canceladaEm()), e.motivoCancelamento(),
                e.avisoSaidaEnviado(), e.avisoEntregaEnviado(), e.referenciaPedido(), e.requisicaoPedido(),
                e.mensagemDoPedido(), e.id());
        if (atualizadas == 0) {
            jdbc.update("INSERT INTO entrega (" + COLUNAS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                    e.id(), e.clienteId(), e.destinatarioId(), e.conversaId(), e.operadorId(), e.nomeCliente(),
                    e.telefone(), e.endereco(), e.pedido(), e.valor(), e.pagamento(), e.observacao(), e.status().name(),
                    tempo(e.criadaEm()), tempo(e.saiuEm()), tempo(e.entregueEm()), tempo(e.canceladaEm()),
                    e.motivoCancelamento(), e.avisoSaidaEnviado(), e.avisoEntregaEnviado(), e.origem().name(),
                    e.referenciaPedido(), e.requisicaoPedido(), e.mensagemDoPedido());
            int posicao = 0;
            for (ItemDeEntrega i : e.itens()) {
                jdbc.update("INSERT INTO entrega_item (entrega_id, posicao, produto_id, nome, quantidade, valor_unitario, imagem) VALUES (?,?,?,?,?,?,?)",
                        e.id(), posicao++, i.produtoId(), i.nome(), i.quantidade(), i.valorUnitario(), i.imagem());
            }
        }
    }

    @Override
    public Optional<Entrega> buscarPorId(UUID id) {
        return jdbc.query("SELECT " + COLUNAS + " FROM entrega WHERE id = ?", (rs, n) -> mapear(rs), id).stream().findFirst();
    }

    @Override
    public List<Entrega> listar(UUID clienteId, StatusEntrega status, Instant de, Instant ate, String busca) {
        StringBuilder sql = new StringBuilder("SELECT " + COLUNAS + " FROM entrega WHERE cliente_id = ?");
        List<Object> args = new ArrayList<>(List.of(clienteId));
        if (status != null) {
            sql.append(" AND status = ?");
            args.add(status.name());
        }
        if (de != null) {
            sql.append(" AND criada_em >= ?");
            args.add(tempo(de));
        }
        if (ate != null) {
            sql.append(" AND criada_em < ?");
            args.add(tempo(ate));
        }
        if (busca != null && !busca.isBlank()) {
            sql.append(" AND (LOWER(nome_cliente) LIKE ? OR telefone LIKE ? OR LOWER(endereco) LIKE ? OR LOWER(pedido) LIKE ?)");
            String termo = "%" + busca.strip().toLowerCase(Locale.ROOT) + "%";
            args.add(termo);
            args.add("%" + busca.strip() + "%");
            args.add(termo);
            args.add(termo);
        }
        sql.append(" ORDER BY criada_em DESC");
        return jdbc.query(sql.toString(), (rs, n) -> mapear(rs), args.toArray());
    }

    @Override
    public Optional<Entrega> abertaPorFinalDoTelefone(UUID clienteId, String ultimosDigitos) {
        return jdbc.query("SELECT " + COLUNAS + " FROM entrega WHERE cliente_id = ? AND status IN ('AGUARDANDO', 'A_CAMINHO') "
                + "AND telefone LIKE ? ORDER BY criada_em DESC", (rs, n) -> mapear(rs), clienteId, "%" + ultimosDigitos)
                .stream().findFirst();
    }

    @Override
    public Optional<ConfiguracaoDeEntrega> buscarConfiguracao(UUID clienteId) {
        return jdbc.query("""
                SELECT avisar_saida, mensagem_saida, avisar_entregue, mensagem_entregue, pix_chave, pix_tipo, pix_nome, aceita_cartao
                FROM configuracao_entrega WHERE cliente_id = ?""", (rs, n) -> new ConfiguracaoDeEntrega(clienteId,
                rs.getBoolean(1), rs.getString(2), rs.getBoolean(3), rs.getString(4), rs.getString(5), rs.getString(6),
                rs.getString(7), rs.getBoolean(8)), clienteId).stream().findFirst();
    }

    @Override
    public void salvar(ConfiguracaoDeEntrega c) {
        entityManager.flush();
        int atualizadas = jdbc.update("""
                UPDATE configuracao_entrega SET avisar_saida = ?, mensagem_saida = ?, avisar_entregue = ?, mensagem_entregue = ?,
                       pix_chave = ?, pix_tipo = ?, pix_nome = ?, aceita_cartao = ? WHERE cliente_id = ?""", c.avisarSaida(),
                c.mensagemSaida(), c.avisarEntregue(), c.mensagemEntregue(), c.pixChave(), c.pixTipo(), c.pixNome(),
                c.aceitaCartao(), c.clienteId());
        if (atualizadas == 0) {
            jdbc.update("""
                    INSERT INTO configuracao_entrega (cliente_id, avisar_saida, mensagem_saida, avisar_entregue, mensagem_entregue,
                           pix_chave, pix_tipo, pix_nome, aceita_cartao) VALUES (?,?,?,?,?,?,?,?,?)""", c.clienteId(),
                    c.avisarSaida(), c.mensagemSaida(), c.avisarEntregue(), c.mensagemEntregue(), c.pixChave(), c.pixTipo(),
                    c.pixNome(), c.aceitaCartao());
        }
    }

    @Override
    public void vincularMensagem(UUID entregaId, UUID mensagemId) {
        entityManager.flush();
        jdbc.update("UPDATE entrega SET mensagem_id = ? WHERE id = ?", mensagemId, entregaId);
    }

    @Override
    public java.util.Map<UUID, List<ItemDeEntrega>> itensPorMensagem(java.util.Collection<UUID> mensagemIds) {
        java.util.Map<UUID, List<ItemDeEntrega>> resultado = new java.util.HashMap<>();
        if (mensagemIds.isEmpty()) {
            return resultado;
        }
        String marcas = String.join(",", java.util.Collections.nCopies(mensagemIds.size(), "?"));
        jdbc.query("SELECT e.mensagem_id, i.produto_id, i.nome, i.quantidade, i.valor_unitario, i.imagem FROM entrega e "
                        + "JOIN entrega_item i ON i.entrega_id = e.id WHERE e.mensagem_id IN (" + marcas + ") "
                        + "ORDER BY e.mensagem_id, i.posicao",
                rs -> {
                    resultado.computeIfAbsent(rs.getObject(1, UUID.class), k -> new java.util.ArrayList<>())
                            .add(new ItemDeEntrega(rs.getString(2), rs.getString(3), rs.getInt(4), rs.getBigDecimal(5), rs.getString(6)));
                }, mensagemIds.toArray());
        return resultado;
    }

    private Entrega mapear(ResultSet rs) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<ItemDeEntrega> itens = jdbc.query("SELECT produto_id, nome, quantidade, valor_unitario, imagem FROM entrega_item WHERE entrega_id = ? ORDER BY posicao",
                (r, n) -> new ItemDeEntrega(r.getString(1), r.getString(2), r.getInt(3), r.getBigDecimal(4), r.getString(5)), id);
        return Entrega.reconstituir(id, rs.getObject("cliente_id", UUID.class), rs.getObject("destinatario_id", UUID.class),
                rs.getObject("conversa_id", UUID.class), rs.getObject("operador_id", UUID.class), rs.getString("nome_cliente"),
                rs.getString("telefone"), rs.getString("endereco"), rs.getString("pedido"), rs.getBigDecimal("valor"),
                rs.getString("pagamento"), rs.getString("observacao"), StatusEntrega.valueOf(rs.getString("status")),
                instante(rs.getTimestamp("criada_em")), instante(rs.getTimestamp("saiu_em")),
                instante(rs.getTimestamp("entregue_em")), instante(rs.getTimestamp("cancelada_em")),
                rs.getString("motivo_cancelamento"), (Boolean) rs.getObject("aviso_saida_enviado"),
                (Boolean) rs.getObject("aviso_entrega_enviado"), OrigemEntrega.valueOf(rs.getString("origem")),
                rs.getString("referencia_pedido"), rs.getString("requisicao_pedido"), rs.getString("mensagem_do_pedido"), itens);
    }

    private static Timestamp tempo(Instant instante) {
        return instante == null ? null : Timestamp.from(instante);
    }

    private static Instant instante(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
