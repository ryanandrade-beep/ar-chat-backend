-- Arquivos enviados pelo painel (imagem, vídeo, áudio, documento). Ficam no banco para o painel exibir e são
-- mandados à Z-API em base64, sem depender de um link público.
CREATE TABLE arquivo (
    id          UUID          PRIMARY KEY,
    cliente_id  UUID          NOT NULL REFERENCES cliente (id),
    nome        VARCHAR(255)  NOT NULL,
    tipo_mime   VARCHAR(120)  NOT NULL,
    tamanho     INTEGER       NOT NULL,
    conteudo    BYTEA         NOT NULL,
    criado_em   TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_arquivo_cliente ON arquivo (cliente_id);
