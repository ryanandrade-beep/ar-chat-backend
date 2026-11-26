ALTER TABLE atendimento ADD COLUMN iniciado_em TIMESTAMP WITH TIME ZONE;
UPDATE atendimento SET iniciado_em = CASE WHEN origem = 'EMPRESA' THEN aberto_em ELSE primeira_resposta_em END;
