CREATE TABLE integracao_webhook (
    cliente_id     UUID PRIMARY KEY REFERENCES cliente (id),
    url            VARCHAR(500) NOT NULL,
    segredo        VARCHAR(100) NOT NULL,
    eventos        VARCHAR(500) NOT NULL,
    ativa          BOOLEAN      NOT NULL DEFAULT TRUE,
    atualizada_em  TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE integracao_envio (
    id                UUID PRIMARY KEY,
    cliente_id        UUID         NOT NULL REFERENCES cliente (id),
    evento            VARCHAR(60)  NOT NULL,
    payload           TEXT         NOT NULL,
    status            VARCHAR(12)  NOT NULL,
    tentativas        INT          NOT NULL DEFAULT 0,
    http_status       INT,
    resposta          VARCHAR(500),
    criado_em         TIMESTAMP WITH TIME ZONE NOT NULL,
    enviado_em        TIMESTAMP WITH TIME ZONE,
    proxima_tentativa TIMESTAMP WITH TIME ZONE,
    CONSTRAINT ck_integracao_envio_status CHECK (status IN ('PENDENTE', 'ENVIADO', 'FALHOU'))
);

CREATE INDEX idx_integracao_envio_pendente ON integracao_envio (status, proxima_tentativa);
CREATE INDEX idx_integracao_envio_cliente ON integracao_envio (cliente_id, criado_em);
