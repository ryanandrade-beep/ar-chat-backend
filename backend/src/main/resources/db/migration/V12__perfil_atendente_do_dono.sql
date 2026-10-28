-- O dono da workspace também atende: ganha um perfil de atendente (dono = true), para ser responsável por
-- conversas e ter a aba "Meus". Esse perfil não entra pelo login de operador nem conta no limite do plano;
-- o e-mail dele é interno (dono-<cliente>@workspace.archat), só para manter a unicidade.
ALTER TABLE operador ADD COLUMN dono BOOLEAN NOT NULL DEFAULT FALSE;
