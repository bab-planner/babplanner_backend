CREATE TABLE inquiries (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    user_id      BIGINT       NOT NULL,
    category     VARCHAR(20)  NOT NULL,
    title        VARCHAR(200) NOT NULL,
    content      TEXT         NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    admin_answer TEXT         NULL,
    answered_at  TIMESTAMP    NULL,
    created_at   TIMESTAMP    NOT NULL,
    updated_at   TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_inquiries_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_inquiries_user ON inquiries (user_id, created_at);
