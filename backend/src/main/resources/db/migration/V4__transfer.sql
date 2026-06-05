-- Epic 2.3 / golden path — Transfer + Leg lifecycle and generated SWIFT messages.

CREATE TABLE transfer (
    transfer_id          UUID PRIMARY KEY,
    quote_id             UUID        NOT NULL REFERENCES quote (quote_id),
    corridor             VARCHAR(16) NOT NULL,
    send_currency        VARCHAR(3)  NOT NULL,
    send_amount_minor    BIGINT      NOT NULL,
    receive_currency     VARCHAR(3)  NOT NULL,
    receive_amount_minor BIGINT      NOT NULL,
    quoted_rate          NUMERIC(20, 8) NOT NULL,
    total_fees_minor     BIGINT      NOT NULL,
    sender_name          VARCHAR(140) NOT NULL,
    recipient_name       VARCHAR(140) NOT NULL,
    recipient_account    VARCHAR(64),
    recipient_bank_bic   VARCHAR(11),
    status               VARCHAR(24) NOT NULL,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL
);

CREATE TABLE leg (
    leg_id        UUID PRIMARY KEY,
    transfer_id   UUID        NOT NULL REFERENCES transfer (transfer_id),
    kind          VARCHAR(16) NOT NULL,
    status        VARCHAR(16) NOT NULL,
    currency      VARCHAR(3)  NOT NULL,
    amount_minor  BIGINT      NOT NULL,
    sequence      INT         NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_leg_transfer ON leg (transfer_id);

CREATE TABLE swift_message (
    swift_id      UUID PRIMARY KEY,
    transfer_id   UUID        NOT NULL REFERENCES transfer (transfer_id),
    leg_id        UUID,
    message_type  VARCHAR(8)  NOT NULL,
    uetr          VARCHAR(36) NOT NULL,
    reference     VARCHAR(16) NOT NULL,
    fin_text      TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_swift_transfer ON swift_message (transfer_id);
