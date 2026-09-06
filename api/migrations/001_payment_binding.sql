-- Aplicar uma vez sobre um banco que já corresponda às entidades anteriores.
-- Não inicializa bancos legados do antigo schema.sql. Faça backup antes da migração.
ALTER TABLE agendamentos
    ADD COLUMN billing_id VARCHAR(255) NULL,
    ADD COLUMN billing_url VARCHAR(2048) NULL,
    ADD COLUMN billing_amount INT NULL,
    ADD CONSTRAINT uk_agendamento_billing UNIQUE (billing_id);
