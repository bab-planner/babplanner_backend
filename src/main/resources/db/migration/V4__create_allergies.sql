CREATE TABLE allergies (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    name       VARCHAR(50) NOT NULL,
    sort_order INT         NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_allergies_name UNIQUE (name)
);
