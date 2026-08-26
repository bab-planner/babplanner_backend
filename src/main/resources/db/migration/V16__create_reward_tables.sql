-- 사용자별 포인트 잔액. 잔액의 단일 source다.
CREATE TABLE reward_accounts (
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    user_id    BIGINT    NOT NULL,
    points     INT       NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_reward_accounts_user UNIQUE (user_id),
    CONSTRAINT fk_reward_accounts_user FOREIGN KEY (user_id) REFERENCES users (id)
);

-- 포인트 변경 원장. type은 계약상 자유 문자열이라 DB enum으로 잠그지 않는다.
-- 새 type이 생겨도 migration 없이 저장할 수 있어야 한다.
CREATE TABLE reward_transactions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    amount      INT          NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    description VARCHAR(200) NULL,
    created_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_reward_transactions_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_reward_transactions_user ON reward_transactions (user_id, created_at);

CREATE TABLE coupons (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    title       VARCHAR(100) NOT NULL,
    badge_label VARCHAR(20)  NULL,
    status      VARCHAR(20)  NOT NULL,
    expires_at  TIMESTAMP    NULL,
    used_at     TIMESTAMP    NULL,
    created_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_coupons_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE INDEX ix_coupons_user_status ON coupons (user_id, status);
