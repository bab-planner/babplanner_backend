CREATE TABLE refresh_sessions (
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    user_id         BIGINT      NOT NULL,
    token_hash      VARCHAR(64) NOT NULL,
    expires_at      TIMESTAMP   NOT NULL,
    revoked_at      TIMESTAMP   NULL,
    created_at      TIMESTAMP   NOT NULL,
    last_rotated_at TIMESTAMP   NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_refresh_sessions_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_sessions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_refresh_sessions_user_id ON refresh_sessions (user_id);
