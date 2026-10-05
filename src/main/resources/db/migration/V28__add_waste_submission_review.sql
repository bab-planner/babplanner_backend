-- 잔반 인증 수동 검토 기록. 누가 언제 검토했는지.
-- reviewed_by_user_id는 FK를 걸지 않는다. 검토자 계정 정리가 검토 기록 때문에
-- 막히지 않게 하고, 회원 탈퇴 삭제 순서에도 영향을 주지 않기 위해서다.
ALTER TABLE waste_submissions ADD COLUMN reviewed_at TIMESTAMP NULL;
ALTER TABLE waste_submissions ADD COLUMN reviewed_by_user_id BIGINT NULL;
