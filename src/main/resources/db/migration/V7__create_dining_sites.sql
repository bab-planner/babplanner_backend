-- 식당(site). OpenAPI에 공개 Site 스키마가 없으므로 내부 FK 소스로만 쓴다.
CREATE TABLE dining_sites (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    code       VARCHAR(30) NOT NULL,
    active     BOOLEAN     NOT NULL,
    sort_order INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_dining_sites_code UNIQUE (code)
);
