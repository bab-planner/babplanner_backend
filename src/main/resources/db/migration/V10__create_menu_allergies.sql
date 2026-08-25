-- Stage 13의 allergies master를 그대로 재사용한다. 별도 master를 만들지 않는다.
CREATE TABLE menu_allergies (
    menu_id    BIGINT NOT NULL,
    allergy_id BIGINT NOT NULL,
    PRIMARY KEY (menu_id, allergy_id),
    CONSTRAINT fk_menu_allergies_menu FOREIGN KEY (menu_id) REFERENCES menus (id),
    CONSTRAINT fk_menu_allergies_allergy FOREIGN KEY (allergy_id) REFERENCES allergies (id)
);
