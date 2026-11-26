ALTER TABLE entrega_item ADD COLUMN imagem VARCHAR(1000);
ALTER TABLE entrega ADD COLUMN mensagem_id UUID;
CREATE INDEX idx_entrega_mensagem ON entrega (mensagem_id);
