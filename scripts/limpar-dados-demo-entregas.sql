-- Remove os dados fictícios de entregas e integração criados por dados-demo-entregas.sql (ids dede0000-*).
BEGIN;
DELETE FROM entrega_item WHERE entrega_id::text LIKE 'dede0000-%';
DELETE FROM entrega WHERE id::text LIKE 'dede0000-%';
DELETE FROM mensagem WHERE id::text LIKE 'dede0000-%';
DELETE FROM integracao_envio WHERE id::text LIKE 'dede0000-%';
DELETE FROM integracao_webhook WHERE segredo = 'whsec_demo0000000000000000000000000000000000000000';
DELETE FROM auditoria WHERE id::text LIKE 'dede0000-%';
COMMIT;
