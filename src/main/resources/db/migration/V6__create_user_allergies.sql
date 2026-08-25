CREATE TABLE user_allergies (
    id         BIGINT    NOT NULL AUTO_INCREMENT,
    user_id    BIGINT    NOT NULL,
    allergy_id BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_allergies_user_allergy UNIQUE (user_id, allergy_id),
    CONSTRAINT fk_user_allergies_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_allergies_allergy FOREIGN KEY (allergy_id) REFERENCES allergies (id)
);

CREATE INDEX ix_user_allergies_user_id ON user_allergies (user_id);
