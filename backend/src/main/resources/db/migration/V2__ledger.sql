-- Epic 3 / WS2 — Double-entry operational ledger (source of truth).
-- Append-only, balanced journal vouchers, typed accounts.

CREATE TABLE account (
    account_code  VARCHAR(64) PRIMARY KEY,
    currency      VARCHAR(3)  NOT NULL,
    account_type  VARCHAR(16) NOT NULL,
    account_role  VARCHAR(48) NOT NULL
);

CREATE TABLE journal_voucher (
    jv_id          UUID PRIMARY KEY,
    movement_type  VARCHAR(32) NOT NULL,
    currency       VARCHAR(3)  NOT NULL,
    amount_minor   BIGINT      NOT NULL,
    transfer_id    UUID,
    leg_id         UUID,
    value_date     DATE        NOT NULL,
    posted_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE posting (
    posting_id    UUID PRIMARY KEY,
    jv_id         UUID        NOT NULL REFERENCES journal_voucher (jv_id),
    account_code  VARCHAR(64) NOT NULL REFERENCES account (account_code),
    direction     VARCHAR(6)  NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor  BIGINT      NOT NULL CHECK (amount_minor > 0),
    currency      VARCHAR(3)  NOT NULL,
    transfer_id   UUID,
    leg_id        UUID,
    value_date    DATE        NOT NULL,
    cleared       BOOLEAN     NOT NULL DEFAULT FALSE
);

CREATE INDEX ix_posting_acct ON posting (account_code);
CREATE INDEX ix_posting_transfer ON posting (transfer_id);
CREATE INDEX ix_jv_transfer ON journal_voucher (transfer_id);

-- Engine-enforced immutability: ledger rows can be inserted, never updated or deleted.
CREATE OR REPLACE FUNCTION ledger_immutable() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'ledger-immutable: % on % is not allowed', TG_OP, TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER posting_immutable BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
CREATE TRIGGER jv_immutable BEFORE UPDATE OR DELETE ON journal_voucher
    FOR EACH ROW EXECUTE FUNCTION ledger_immutable();
