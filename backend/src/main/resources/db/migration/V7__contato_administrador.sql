-- Dados de contato do administrador (tela "Minha conta"), usados também para recuperar o acesso no futuro.
ALTER TABLE administrador ADD COLUMN email    VARCHAR(160);
ALTER TABLE administrador ADD COLUMN telefone VARCHAR(13);
CREATE UNIQUE INDEX uk_administrador_email ON administrador (email);
