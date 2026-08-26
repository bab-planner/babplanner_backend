CREATE TABLE waste_submissions (
    id                     BIGINT      NOT NULL AUTO_INCREMENT,
    user_id                BIGINT      NOT NULL,
    menu_id                BIGINT      NOT NULL,
    status                 VARCHAR(20) NOT NULL,
    -- 저장소가 발급한 키. 서버 파일 경로가 아니며 API로 노출하지 않는다.
    image_storage_key      VARCHAR(200) NOT NULL,
    captured_at            TIMESTAMP   NOT NULL,
    waste_rate             DOUBLE      NULL,
    confidence             DOUBLE      NULL,
    -- 리워드 지급 정책이 확정되지 않아 항상 NULL이다. 0/false로 채우지 않는다.
    earned_points          INT         NULL,
    coupon_issued          BOOLEAN     NULL,
    -- 처리 중 상태가 언제부터인지 판단하기 위한 내부 필드. API에 노출하지 않는다.
    processing_started_at  TIMESTAMP   NULL,
    created_at             TIMESTAMP   NOT NULL,
    updated_at             TIMESTAMP   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_waste_submissions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_waste_submissions_menu FOREIGN KEY (menu_id) REFERENCES menus (id)
);

CREATE INDEX ix_waste_submissions_user ON waste_submissions (user_id, created_at);
CREATE INDEX ix_waste_submissions_status ON waste_submissions (status);
