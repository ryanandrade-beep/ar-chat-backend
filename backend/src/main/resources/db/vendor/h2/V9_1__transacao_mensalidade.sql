-- H2 (testes): a restrição da V1 não tem nome fixo, então a tabela (vazia nos testes) é recriada
DROP TABLE transacao;
CREATE TABLE transacao (
    id           UUID           PRIMARY KEY,
    cliente_id   UUID           NOT NULL REFERENCES cliente (id),
    mensagem_id  UUID           REFERENCES mensagem (id),
    tipo         VARCHAR(15)    NOT NULL,
    valor        NUMERIC(12, 2) NOT NULL,
    saldo_apos   NUMERIC(12, 2) NOT NULL,
    data_hora    TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT transacao_tipo_check CHECK (tipo IN ('CREDITO', 'DEBITO', 'ESTORNO', 'AJUSTE_LIMITE', 'MENSALIDADE'))
);
CREATE INDEX idx_transacao_cliente ON transacao (cliente_id, data_hora);
