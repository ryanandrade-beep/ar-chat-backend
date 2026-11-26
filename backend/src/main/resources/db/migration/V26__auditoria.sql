CREATE TABLE auditoria (
    id          UUID PRIMARY KEY,
    cliente_id  UUID         NOT NULL REFERENCES cliente (id),
    autor_id    UUID,
    autor_nome  VARCHAR(120) NOT NULL,
    acao        VARCHAR(60)  NOT NULL,
    recurso     VARCHAR(200),
    detalhe     VARCHAR(500),
    criada_em   TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_auditoria_cliente ON auditoria (cliente_id, criada_em);
