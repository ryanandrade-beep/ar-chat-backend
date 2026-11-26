CREATE TABLE entrega (
    id                    UUID PRIMARY KEY,
    cliente_id            UUID         NOT NULL REFERENCES cliente (id),
    destinatario_id       UUID         NOT NULL REFERENCES destinatario (id),
    conversa_id           UUID         NOT NULL REFERENCES conversa (id),
    operador_id           UUID,
    nome_cliente          VARCHAR(120) NOT NULL,
    telefone              VARCHAR(40)  NOT NULL,
    endereco              VARCHAR(300) NOT NULL,
    pedido                VARCHAR(1000) NOT NULL,
    valor                 NUMERIC(10, 2),
    pagamento             VARCHAR(20),
    observacao            VARCHAR(500),
    status                VARCHAR(12)  NOT NULL,
    criada_em             TIMESTAMP WITH TIME ZONE NOT NULL,
    saiu_em               TIMESTAMP WITH TIME ZONE,
    entregue_em           TIMESTAMP WITH TIME ZONE,
    cancelada_em          TIMESTAMP WITH TIME ZONE,
    motivo_cancelamento   VARCHAR(300),
    aviso_saida_enviado   BOOLEAN,
    aviso_entrega_enviado BOOLEAN,
    origem                VARCHAR(10)  NOT NULL DEFAULT 'OPERADOR',
    referencia_pedido     VARCHAR(60),
    requisicao_pedido     VARCHAR(60),
    mensagem_do_pedido    VARCHAR(100),
    CONSTRAINT ck_entrega_status CHECK (status IN ('AGUARDANDO', 'A_CAMINHO', 'ENTREGUE', 'CANCELADA')),
    CONSTRAINT ck_entrega_origem CHECK (origem IN ('OPERADOR', 'CATALOGO'))
);

CREATE TABLE entrega_item (
    entrega_id     UUID          NOT NULL REFERENCES entrega (id) ON DELETE CASCADE,
    posicao        INTEGER       NOT NULL,
    produto_id     VARCHAR(60),
    nome           VARCHAR(200)  NOT NULL,
    quantidade     INTEGER       NOT NULL,
    valor_unitario NUMERIC(10, 2) NOT NULL,
    PRIMARY KEY (entrega_id, posicao)
);
CREATE INDEX idx_entrega_cliente_status ON entrega (cliente_id, status, criada_em);

CREATE TABLE configuracao_entrega (
    cliente_id        UUID PRIMARY KEY REFERENCES cliente (id),
    avisar_saida      BOOLEAN       NOT NULL DEFAULT TRUE,
    mensagem_saida    VARCHAR(500)  NOT NULL,
    avisar_entregue   BOOLEAN       NOT NULL DEFAULT TRUE,
    mensagem_entregue VARCHAR(500)  NOT NULL,
    pix_chave         VARCHAR(140),
    pix_tipo          VARCHAR(12),
    pix_nome          VARCHAR(80),
    aceita_cartao     BOOLEAN       NOT NULL DEFAULT TRUE
);
