-- 예상 식수 인원 예측 이력.
--
-- 같은 끼니를 다시 예측해도 기존 행을 덮지 않고 새 행을 넣는다. 예측은
-- 모델과 시점에 따라 달라지는 값이라, 덮어쓰면 "그때 무엇을 보고 그렇게
-- 발주했는가"를 나중에 확인할 수 없다.
--
-- 그래서 (site_id, service_date, meal_type)에 UNIQUE를 걸지 않는다.
CREATE TABLE demand_predictions (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    site_id          BIGINT       NOT NULL,
    service_date     DATE         NOT NULL,
    meal_type        VARCHAR(20)  NOT NULL,
    predicted_people INT          NOT NULL,
    model_version    VARCHAR(100) NOT NULL,
    -- 사용자 식별자가 아니다. 한 번의 실행을 가리키는 상관 ID다.
    request_id       VARCHAR(100) NOT NULL,
    created_at       TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_demand_predictions_site FOREIGN KEY (site_id) REFERENCES dining_sites (id)
);

-- 조회는 항상 사이트 + 날짜(+ 끼니)로 들어오고, 최신 것부터 본다.
CREATE INDEX ix_demand_predictions_lookup
    ON demand_predictions (site_id, service_date, meal_type, created_at);
