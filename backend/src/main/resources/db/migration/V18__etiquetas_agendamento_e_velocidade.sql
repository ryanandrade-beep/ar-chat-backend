CREATE TABLE destinatario_etiqueta (
    destinatario_id UUID        NOT NULL REFERENCES destinatario (id) ON DELETE CASCADE,
    etiqueta        VARCHAR(30) NOT NULL,
    PRIMARY KEY (destinatario_id, etiqueta)
);
CREATE INDEX idx_destinatario_etiqueta_nome ON destinatario_etiqueta (etiqueta);

CREATE TABLE campanha_envio (
    campanha_id        UUID PRIMARY KEY REFERENCES campanha (id),
    agendada_para      TIMESTAMP WITH TIME ZONE,
    intervalo_segundos INTEGER     NOT NULL DEFAULT 0,
    etiqueta           VARCHAR(30)
);

CREATE TABLE liberacao_pendente (
    mensagem_id UUID PRIMARY KEY REFERENCES mensagem (id),
    liberar_em  TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_liberacao_pendente_quando ON liberacao_pendente (liberar_em);
