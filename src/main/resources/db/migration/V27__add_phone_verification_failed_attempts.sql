-- 인증번호 오입력 횟수. 한도를 넘기면 해당 인증 요청을 무효로 본다.
ALTER TABLE phone_verifications ADD COLUMN failed_attempts INT NOT NULL DEFAULT 0;
