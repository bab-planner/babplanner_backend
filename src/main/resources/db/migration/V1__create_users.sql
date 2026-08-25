CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    name          VARCHAR(50)  NOT NULL,
    phone         VARCHAR(20)  NOT NULL,
    student_id    VARCHAR(20)  NOT NULL,
    gender        VARCHAR(10)  NULL,
    role          VARCHAR(10)  NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_phone UNIQUE (phone),
    CONSTRAINT uk_users_student_id UNIQUE (student_id)
);
