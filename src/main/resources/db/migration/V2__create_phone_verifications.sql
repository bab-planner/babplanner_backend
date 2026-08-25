CREATE TABLE phone_verifications (
    id                            BIGINT      NOT NULL AUTO_INCREMENT,
    phone                         VARCHAR(20) NOT NULL,
    code_hash                     VARCHAR(64) NOT NULL,
    expires_at                    TIMESTAMP   NOT NULL,
    resend_available_at           TIMESTAMP   NOT NULL,
    verified_at                   TIMESTAMP   NULL,
    verification_token_hash       VARCHAR(64) NULL,
    verification_token_expires_at TIMESTAMP   NULL,
    token_used_at                 TIMESTAMP   NULL,
    created_at                    TIMESTAMP   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_phone_verifications_token_hash UNIQUE (verification_token_hash)
);

CREATE INDEX ix_phone_verifications_phone ON phone_verifications (phone);
