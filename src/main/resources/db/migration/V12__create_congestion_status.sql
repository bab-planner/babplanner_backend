-- 사이트별 현재 혼잡 상태 1행. 아직 AI observation history 요구가 없어
-- event table을 만들지 않는다. 향후 /internal/v1 수집이 붙으면 이 행을
-- 갱신하거나 history table을 추가하면 된다.
CREATE TABLE congestion_status (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    site_id         BIGINT       NOT NULL,
    level           VARCHAR(20)  NOT NULL,
    people_count    INT          NULL,
    display_message VARCHAR(100) NULL,
    source          VARCHAR(30)  NULL,
    observed_at     TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_congestion_status_site UNIQUE (site_id),
    CONSTRAINT fk_congestion_status_site FOREIGN KEY (site_id) REFERENCES dining_sites (id)
);
