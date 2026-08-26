CREATE TABLE surveys (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(500) NULL,
    starts_at   TIMESTAMP    NOT NULL,
    ends_at     TIMESTAMP    NOT NULL,
    created_at  TIMESTAMP    NOT NULL,
    PRIMARY KEY (id)
);

CREATE INDEX ix_surveys_window ON surveys (starts_at, ends_at);

CREATE TABLE survey_questions (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    survey_id   BIGINT       NOT NULL,
    text        VARCHAR(300) NOT NULL,
    type        VARCHAR(20)  NOT NULL,
    is_required BOOLEAN      NOT NULL,
    sort_order  INT          NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_survey_questions_survey FOREIGN KEY (survey_id) REFERENCES surveys (id)
);

CREATE TABLE survey_question_options (
    question_id BIGINT       NOT NULL,
    sort_order  INT          NOT NULL,
    label       VARCHAR(100) NOT NULL,
    PRIMARY KEY (question_id, sort_order),
    CONSTRAINT fk_survey_question_options_question
        FOREIGN KEY (question_id) REFERENCES survey_questions (id)
);

-- (user_id, survey_id) unique로 중복 참여를 DB에서 막는다.
CREATE TABLE survey_responses (
    id           BIGINT    NOT NULL AUTO_INCREMENT,
    survey_id    BIGINT    NOT NULL,
    user_id      BIGINT    NOT NULL,
    submitted_at TIMESTAMP NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_survey_responses_user_survey UNIQUE (user_id, survey_id),
    CONSTRAINT fk_survey_responses_survey FOREIGN KEY (survey_id) REFERENCES surveys (id),
    CONSTRAINT fk_survey_responses_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE survey_answers (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    response_id BIGINT        NOT NULL,
    question_id BIGINT        NOT NULL,
    scale_value INT           NULL,
    text_value  VARCHAR(1000) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_survey_answers_response_question UNIQUE (response_id, question_id),
    CONSTRAINT fk_survey_answers_response FOREIGN KEY (response_id) REFERENCES survey_responses (id),
    CONSTRAINT fk_survey_answers_question FOREIGN KEY (question_id) REFERENCES survey_questions (id)
);

CREATE TABLE survey_answer_options (
    answer_id  BIGINT       NOT NULL,
    sort_order INT          NOT NULL,
    label      VARCHAR(100) NOT NULL,
    PRIMARY KEY (answer_id, sort_order),
    CONSTRAINT fk_survey_answer_options_answer FOREIGN KEY (answer_id) REFERENCES survey_answers (id)
);
