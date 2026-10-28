-- Nova movimentação: cobrança da mensalidade do plano
ALTER TABLE transacao DROP CONSTRAINT transacao_tipo_check;
ALTER TABLE transacao ADD CONSTRAINT transacao_tipo_check
    CHECK (tipo IN ('CREDITO', 'DEBITO', 'ESTORNO', 'AJUSTE_LIMITE', 'MENSALIDADE'));
