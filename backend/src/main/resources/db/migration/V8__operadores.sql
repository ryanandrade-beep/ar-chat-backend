-- Operadores: cada workspace (cliente) tem vários operadores, cada um com login próprio (e-mail único na plataforma).
-- O dono da workspace (conta_acesso) cadastra e gerencia os operadores; cada conversa tem um operador responsável.
CREATE TABLE operador (
    id          UUID          PRIMARY KEY,
    cliente_id  UUID          NOT NULL REFERENCES cliente (id),
    nome        VARCHAR(120)  NOT NULL,
    email       VARCHAR(160)  NOT NULL UNIQUE,
    senha_hash  VARCHAR(100)  NOT NULL,
    ativo       BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_operador_cliente ON operador (cliente_id);

ALTER TABLE conversa ADD COLUMN operador_id UUID REFERENCES operador (id);
CREATE INDEX idx_conversa_operador ON conversa (operador_id);
