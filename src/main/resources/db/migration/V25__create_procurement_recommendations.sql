-- 발주량 추천 실행과 그 항목.
--
-- 한 번의 실행(run)이 여러 식재료 항목을 낳는다. 항목만 저장하면 어느
-- 실행에서 나온 값인지, 어떤 예측을 근거로 삼았는지 알 수 없다.
--
-- 예측과 마찬가지로 다시 실행해도 덮어쓰지 않는다. 지난 추천이 사라지면
-- "그때 무엇을 보고 그렇게 발주했는가"를 확인할 수 없다.
CREATE TABLE procurement_runs (
    id                    BIGINT       NOT NULL AUTO_INCREMENT,
    site_id               BIGINT       NOT NULL,
    service_date          DATE         NOT NULL,
    meal_type             VARCHAR(20)  NOT NULL,
    -- 어떤 예측을 근거로 삼았는지. 예측 없이 추천하지 않는다.
    demand_prediction_id  BIGINT       NOT NULL,
    model_version         VARCHAR(100) NOT NULL,
    -- 사용자 식별자가 아니다. 한 번의 실행을 가리키는 상관 ID다.
    request_id            VARCHAR(100) NOT NULL,
    created_at            TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_procurement_runs_site FOREIGN KEY (site_id) REFERENCES dining_sites (id),
    CONSTRAINT fk_procurement_runs_prediction
        FOREIGN KEY (demand_prediction_id) REFERENCES demand_predictions (id)
);

CREATE INDEX ix_procurement_runs_lookup
    ON procurement_runs (site_id, service_date, meal_type, created_at);

CREATE TABLE procurement_items (
    id                   BIGINT        NOT NULL AUTO_INCREMENT,
    run_id               BIGINT        NOT NULL,
    -- 요청에 들어온 순서. 화면이 관리자가 적은 순서대로 보여 줄 수 있어야 한다.
    position             INT           NOT NULL,
    ingredient_name      VARCHAR(100)  NOT NULL,
    -- 수량과 단위를 분리해 저장한다. "10kg" 같은 문자열은 읽는 쪽마다 다르게
    -- 해석되고 단위를 바꾸려면 문자열을 다시 뜯어야 한다.
    unit                 VARCHAR(20)   NOT NULL,
    planned_quantity     DOUBLE        NOT NULL,
    recommended_quantity DOUBLE        NOT NULL,
    adjustment_rate      DOUBLE        NULL,
    reason               VARCHAR(500)  NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_procurement_items_run
        FOREIGN KEY (run_id) REFERENCES procurement_runs (id),
    CONSTRAINT uk_procurement_items_position UNIQUE (run_id, position)
);
