-- 혼잡도 관측 이력.
--
-- congestion_status는 site_id에 UNIQUE가 걸린 "사이트별 현재값 1행" 테이블이라
-- 시계열을 만들 수 없다. 그렇다고 없는 값을 지어낼 수는 없으므로, 읽기 쪽이
-- 진짜 DB를 볼 수 있도록 이력 테이블만 먼저 만든다.
--
-- 이 단계에서는 수집 endpoint(POST /internal/v1/congestion-observations)를
-- 만들지 않는다. 따라서 아래 백필 외에는 아직 쓰는 주체가 없다.
CREATE TABLE congestion_observations (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    site_id         BIGINT       NOT NULL,
    level           VARCHAR(20)  NOT NULL,
    people_count    INT          NULL,
    display_message VARCHAR(100) NULL,
    source          VARCHAR(30)  NULL,
    observed_at     TIMESTAMP    NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_congestion_observations_site FOREIGN KEY (site_id) REFERENCES dining_sites (id)
);

-- 조회는 항상 사이트 + 기간으로 들어온다.
CREATE INDEX ix_congestion_observations_site_observed
    ON congestion_observations (site_id, observed_at);

-- 이미 저장돼 있던 현재값을 그 사이트의 첫 관측으로 옮겨 둔다. 새로 만든
-- 숫자가 아니라 congestion_status에 실제로 있던 행이며, source도 그대로
-- 따라온다. 그래서 DEV_SEED로 들어온 값은 계속 DEV_SEED로 보인다.
INSERT INTO congestion_observations (site_id, level, people_count, display_message, source,
                                     observed_at, created_at)
SELECT c.site_id, c.level, c.people_count, c.display_message, c.source,
       c.observed_at, c.updated_at
FROM congestion_status c;
