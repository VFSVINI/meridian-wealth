-- Item #13: livro de movimentações (append-only), custo em reais ao câmbio de cada compra e idempotência.

-- Custo médio em reais (ao câmbio de cada compra). Nulo = "não informado": posição em dólar aberta antes do livro, sem câmbio de compra.
ALTER TABLE positions ADD COLUMN average_cost_brl NUMERIC(28, 10);
ALTER TABLE positions ADD CONSTRAINT positions_cost_brl_ck CHECK (average_cost_brl IS NULL OR average_cost_brl > 0);
UPDATE positions SET average_cost_brl = average_price WHERE currency = 'BRL';

-- O livro. Uma entrada nunca muda: o gatilho abaixo bloqueia UPDATE. A exclusão só acontece em cascata ao apagar o usuário.
-- position_id não é chave estrangeira de propósito: a posição some ao ser zerada, o livro guarda a história.
CREATE TABLE ledger_entries (
    seq                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    id                     UUID         NOT NULL UNIQUE,
    user_id                UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    position_id            UUID         NOT NULL,
    asset_key              VARCHAR(120) NOT NULL,
    symbol                 VARCHAR(32),
    asset_type             VARCHAR(32)  NOT NULL,
    label                  VARCHAR(120) NOT NULL,
    entry_type             VARCHAR(16)  NOT NULL,
    trade_date             DATE         NOT NULL,
    quantity               NUMERIC(28, 10) NOT NULL,
    unit_price             NUMERIC(28, 10),
    fees                   NUMERIC(28, 10) NOT NULL DEFAULT 0,
    currency               CHAR(3)      NOT NULL,
    fx_brl_per_usd         NUMERIC(28, 10),
    fx_source              VARCHAR(8),
    fx_date                DATE,
    quantity_after         NUMERIC(28, 10) NOT NULL,
    average_price_after    NUMERIC(28, 10),
    average_cost_brl_after NUMERIC(28, 10),
    realized_pnl           NUMERIC(28, 2),
    realized_pnl_brl       NUMERIC(28, 2),
    version_after          BIGINT       NOT NULL,
    idempotency_key        VARCHAR(64),
    request_hash           CHAR(64),
    created_at             TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ledger_type_ck CHECK (entry_type IN ('OPENING', 'BUY', 'SELL', 'ADJUSTMENT', 'WRITE_OFF')),
    CONSTRAINT ledger_currency_ck CHECK (currency IN ('BRL', 'USD')),
    CONSTRAINT ledger_fx_source_ck CHECK (fx_source IS NULL OR fx_source IN ('PTAX', 'USER', 'UNKNOWN')),
    CONSTRAINT ledger_quantity_ck CHECK (quantity > 0 AND quantity_after >= 0),
    CONSTRAINT ledger_price_ck CHECK (unit_price IS NULL OR unit_price > 0),
    CONSTRAINT ledger_fees_ck CHECK (fees >= 0),
    CONSTRAINT ledger_fx_ck CHECK (fx_brl_per_usd IS NULL OR fx_brl_per_usd > 0),
    -- só a venda tem resultado realizado
    CONSTRAINT ledger_realized_ck CHECK ((entry_type = 'SELL') = (realized_pnl IS NOT NULL)),
    -- quem tem chave de idempotência tem o hash do conteúdo
    CONSTRAINT ledger_idem_ck CHECK ((idempotency_key IS NULL) = (request_hash IS NULL))
);

-- A mesma Idempotency-Key do mesmo usuário nunca grava duas entradas: é o que decide duas requisições simultâneas.
CREATE UNIQUE INDEX ledger_idempotency_uq ON ledger_entries (user_id, idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX ledger_user_asset_idx ON ledger_entries (user_id, asset_key, seq);
CREATE INDEX ledger_user_date_idx ON ledger_entries (user_id, trade_date, seq);

CREATE FUNCTION ledger_entries_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger_entries é só acrescentado: a entrada % não pode ser alterada', OLD.id USING ERRCODE = 'integrity_constraint_violation';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER ledger_entries_no_update BEFORE UPDATE ON ledger_entries FOR EACH ROW EXECUTE FUNCTION ledger_entries_immutable();

-- As posições que já existiam (item #12) ganham a entrada de abertura, para o livro reproduzir a posição. Em dólar, o câmbio da
-- compra nunca foi registrado: fica UNKNOWN e nulo, nunca inventado.
INSERT INTO ledger_entries (id, user_id, position_id, asset_key, symbol, asset_type, label, entry_type, trade_date, quantity, unit_price, fees, currency,
                            fx_brl_per_usd, fx_source, fx_date, quantity_after, average_price_after, average_cost_brl_after, version_after, created_at)
SELECT gen_random_uuid(), user_id, id, asset_key, symbol, asset_type, label, 'OPENING', created_at::date, quantity, average_price, 0, currency,
       NULL, CASE WHEN currency = 'USD' THEN 'UNKNOWN' END, NULL, quantity, average_price, average_cost_brl, version, created_at
FROM positions;
