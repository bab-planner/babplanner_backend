-- 시연 데이터 생성 기록.
--
-- Demo Data Seeder(local/dev 프로필, 명시적 명령으로만 실행)가 만든 행의 소유권을
-- 기록한다. 이 표에 등록된 행만 정리 명령이 지운다. 실제 사용자/운영 데이터는
-- 여기에 등록되지 않으므로 정리 대상이 될 수 없다.
--
-- 다른 표를 참조하는 FK는 두지 않는다. 대상 행이 지워져도 기록이 정리를 막지 않고,
-- 정리 명령이 사라진 행의 기록을 함께 치운다.
CREATE TABLE demo_seed_records (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    entity_type VARCHAR(40) NOT NULL,
    entity_id   BIGINT      NOT NULL,
    created_at  TIMESTAMP   NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_demo_seed_records_entity UNIQUE (entity_type, entity_id)
);
