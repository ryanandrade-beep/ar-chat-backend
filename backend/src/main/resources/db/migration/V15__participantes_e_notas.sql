-- Atendimento em equipe: outros operadores podem participar de uma conversa que já tem responsável,
-- cada mensagem guarda quem a enviou e a equipe troca notas internas (não vão para o WhatsApp nem são cobradas).
CREATE TABLE conversa_participante (
    conversa_id  UUID NOT NULL REFERENCES conversa (id),
    operador_id  UUID NOT NULL REFERENCES operador (id),
    desde        TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (conversa_id, operador_id)
);

ALTER TABLE mensagem ADD COLUMN operador_id UUID REFERENCES operador (id);
ALTER TABLE mensagem ADD COLUMN interna BOOLEAN NOT NULL DEFAULT FALSE;
