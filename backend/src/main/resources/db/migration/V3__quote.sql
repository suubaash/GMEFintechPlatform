-- Epic 2 — Quoting (2.1.1 FX fetch, 2.1.2 fees, 2.1.3 quote object).

CREATE TABLE quote (
    quote_id             UUID PRIMARY KEY,
    corridor             VARCHAR(16)   NOT NULL,
    send_currency        VARCHAR(3)    NOT NULL,
    send_amount_minor    BIGINT        NOT NULL,
    receive_currency     VARCHAR(3)    NOT NULL,
    receive_amount_minor BIGINT        NOT NULL,
    mid_rate             NUMERIC(20, 8) NOT NULL,
    margin_bps           INT           NOT NULL,
    quoted_rate          NUMERIC(20, 8) NOT NULL,
    total_fees_minor     BIGINT        NOT NULL,
    rate_source_id       VARCHAR(64)   NOT NULL,
    fetch_ts             TIMESTAMPTZ   NOT NULL,
    rate_ttl_seconds     BIGINT        NOT NULL,
    eta_minutes          INT           NOT NULL,
    status               VARCHAR(16)   NOT NULL,
    created_at           TIMESTAMPTZ   NOT NULL,
    expires_at           TIMESTAMPTZ   NOT NULL
);

CREATE TABLE fee_line_item (
    fee_id          UUID PRIMARY KEY,
    quote_id        UUID        NOT NULL REFERENCES quote (quote_id),
    fee_type        VARCHAR(16) NOT NULL,
    amount_minor    BIGINT      NOT NULL,
    currency        VARCHAR(3)  NOT NULL,
    resolved_layer  VARCHAR(16) NOT NULL
);

CREATE INDEX ix_fee_quote ON fee_line_item (quote_id);
