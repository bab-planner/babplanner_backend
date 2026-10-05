-- 포인트 지급의 출처 참조. 같은 출처(예: 잔반 제출 한 건)로 두 번 지급되지 않게
-- DB 차원에서 막는다. 기존 행과 시드 행은 NULL이며, NULL은 unique 대상이 아니다.
ALTER TABLE reward_transactions ADD COLUMN reference_type VARCHAR(30) NULL;
ALTER TABLE reward_transactions ADD COLUMN reference_id BIGINT NULL;

CREATE UNIQUE INDEX uk_reward_transactions_reference
    ON reward_transactions (reference_type, reference_id);
