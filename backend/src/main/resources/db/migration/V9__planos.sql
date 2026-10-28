-- Planos criados pelo backoffice: mensalidade, operadores incluídos e preço por mensagem (normal e urgente).
-- Cada workspace tem no máximo uma assinatura; sem assinatura valem os preços padrão (R$ 0,25 e R$ 0,50).
CREATE TABLE plano (
    id                    UUID          PRIMARY KEY,
    nome                  VARCHAR(60)   NOT NULL UNIQUE,
    mensalidade           NUMERIC(10,2) NOT NULL CHECK (mensalidade >= 0),
    operadores_incluidos  INTEGER       NOT NULL CHECK (operadores_incluidos >= 1),
    preco_normal          NUMERIC(5,2)  NOT NULL CHECK (preco_normal > 0),
    preco_urgente         NUMERIC(5,2)  NOT NULL CHECK (preco_urgente > 0),
    ativo                 BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em             TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE TABLE assinatura (
    cliente_id      UUID         PRIMARY KEY REFERENCES cliente (id),
    plano_id        UUID         NOT NULL REFERENCES plano (id),
    situacao        VARCHAR(10)  NOT NULL CHECK (situacao IN ('ATIVA', 'EM_ATRASO')),
    inicio          TIMESTAMP WITH TIME ZONE NOT NULL,
    ultima_cobranca TIMESTAMP WITH TIME ZONE
);

-- A troca da restrição de tipo da tabela transacao (nova movimentação MENSALIDADE) fica em
-- db/vendor/{postgresql,h2}/V9_1, porque cada banco dá um nome diferente à restrição criada na V1.
