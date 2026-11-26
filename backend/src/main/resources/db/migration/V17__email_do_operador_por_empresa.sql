ALTER TABLE operador DROP CONSTRAINT operador_email_key;
ALTER TABLE operador ADD CONSTRAINT operador_empresa_email_key UNIQUE (cliente_id, email);
