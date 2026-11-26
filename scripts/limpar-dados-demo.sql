-- Remove tudo o que dados-demo.sql criou (contatos 5500000*, suas conversas, atendimentos, mensagens, etiquetas e campanhas).
BEGIN;
CREATE TEMP TABLE demo_conversas AS
    SELECT c.id FROM conversa c JOIN destinatario d ON d.id = c.destinatario_id WHERE d.telefone LIKE '5500000%';
CREATE TEMP TABLE demo_campanhas AS
    SELECT DISTINCT campanha_id AS id FROM mensagem WHERE campanha_id IS NOT NULL AND conversa_id IN (SELECT id FROM demo_conversas);
DELETE FROM liberacao_pendente WHERE mensagem_id IN (SELECT id FROM mensagem WHERE conversa_id IN (SELECT id FROM demo_conversas));
DELETE FROM mensagem WHERE conversa_id IN (SELECT id FROM demo_conversas);
DELETE FROM atendimento WHERE conversa_id IN (SELECT id FROM demo_conversas);
DELETE FROM conversa_participante WHERE conversa_id IN (SELECT id FROM demo_conversas);
DELETE FROM conversa WHERE id IN (SELECT id FROM demo_conversas);
DELETE FROM campanha_envio WHERE campanha_id IN (SELECT id FROM demo_campanhas);
DELETE FROM campanha WHERE id IN (SELECT id FROM demo_campanhas);
DELETE FROM destinatario WHERE telefone LIKE '5500000%';
COMMIT;
