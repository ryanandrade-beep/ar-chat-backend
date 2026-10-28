-- Cada empresa conecta o próprio número: o backoffice vincula a ela uma instância da Z-API (ID e token).
-- Sem instância própria, a empresa usa a instância padrão da plataforma (configurada no .env).
CREATE TABLE instancia_whatsapp (
    cliente_id  UUID          PRIMARY KEY REFERENCES cliente (id),
    instancia   VARCHAR(64)   NOT NULL UNIQUE,
    token       VARCHAR(128)  NOT NULL,
    vinculada_em TIMESTAMP WITH TIME ZONE NOT NULL
);
