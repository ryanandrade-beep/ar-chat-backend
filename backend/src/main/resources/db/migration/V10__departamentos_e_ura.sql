-- Departamentos da workspace e URA (atendimento automático): o cliente final escolhe o departamento pelo menu.
CREATE TABLE departamento (
    id          UUID          PRIMARY KEY,
    cliente_id  UUID          NOT NULL REFERENCES cliente (id),
    nome        VARCHAR(60)   NOT NULL,
    ativo       BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uk_departamento_nome UNIQUE (cliente_id, nome)
);

CREATE TABLE configuracao_ura (
    cliente_id               UUID          PRIMARY KEY REFERENCES cliente (id),
    ativa                    BOOLEAN       NOT NULL DEFAULT FALSE,
    mensagem_boas_vindas     VARCHAR(1000) NOT NULL,
    mensagem_pergunta        VARCHAR(1000) NOT NULL,
    maximo_tentativas        INTEGER       NOT NULL DEFAULT 3,
    departamento_padrao_id   UUID          REFERENCES departamento (id),
    ignorar_grupos           BOOLEAN       NOT NULL DEFAULT TRUE
);

ALTER TABLE conversa ADD COLUMN departamento_id UUID REFERENCES departamento (id);
ALTER TABLE conversa ADD COLUMN aguardando_ura  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE conversa ADD COLUMN tentativas_ura  INTEGER NOT NULL DEFAULT 0;

-- Mensagens enviadas pela URA: saem pelo canal, mas não são cobradas nem passam pela fila
ALTER TABLE mensagem ADD COLUMN automatica BOOLEAN NOT NULL DEFAULT FALSE;
