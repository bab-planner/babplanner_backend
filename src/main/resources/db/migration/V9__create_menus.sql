-- 날짜/시간대별 실제 제공 식단 instance. 장기 메뉴 template이 아니다.
CREATE TABLE menus (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    site_id         BIGINT       NOT NULL,
    service_date    DATE         NOT NULL,
    meal_type       VARCHAR(20)  NOT NULL,
    category        VARCHAR(20)  NOT NULL,
    name            VARCHAR(100) NOT NULL,
    description     TEXT         NULL,
    image_url       VARCHAR(500) NULL,
    calories_kcal   INT          NULL,
    protein_g       DOUBLE       NULL,
    carbohydrate_g  DOUBLE       NULL,
    fat_g           DOUBLE       NULL,
    sodium_mg       INT          NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_menus_site FOREIGN KEY (site_id) REFERENCES dining_sites (id)
);

CREATE INDEX ix_menus_service_date ON menus (service_date);
CREATE INDEX ix_menus_site_date ON menus (site_id, service_date);

-- 구성 메뉴(items). 순서를 보존한다.
CREATE TABLE menu_items (
    menu_id    BIGINT       NOT NULL,
    sort_order INT          NOT NULL,
    name       VARCHAR(100) NOT NULL,
    PRIMARY KEY (menu_id, sort_order),
    CONSTRAINT fk_menu_items_menu FOREIGN KEY (menu_id) REFERENCES menus (id)
);
