-- T-1.1.1-01  Party/account model with unverified state
-- T-1.1.1-02  OTP entity with single-use flag and TTL

CREATE TABLE party (
    party_id    UUID PRIMARY KEY,
    party_type  VARCHAR(20)  NOT NULL DEFAULT 'INDIVIDUAL',
    email       VARCHAR(255),
    phone       VARCHAR(32),
    full_name   VARCHAR(255),
    status      VARCHAR(20)  NOT NULL DEFAULT 'UNVERIFIED',
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Duplicate-identifier guard at the storage layer (T-1.1.1-10)
CREATE UNIQUE INDEX uq_party_email ON party (email) WHERE email IS NOT NULL;
CREATE UNIQUE INDEX uq_party_phone ON party (phone) WHERE phone IS NOT NULL;

CREATE TABLE otp (
    otp_id      UUID PRIMARY KEY,
    identifier  VARCHAR(255) NOT NULL,
    code        VARCHAR(10)  NOT NULL,
    consumed    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    expires_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_otp_identifier ON otp (identifier);
