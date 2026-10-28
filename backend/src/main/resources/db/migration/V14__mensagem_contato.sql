-- Mensagem com contato compartilhado (cartão de contato do WhatsApp): nome e telefone de quem foi compartilhado.
ALTER TABLE mensagem ADD COLUMN contato_nome     VARCHAR(120);
ALTER TABLE mensagem ADD COLUMN contato_telefone VARCHAR(20);
