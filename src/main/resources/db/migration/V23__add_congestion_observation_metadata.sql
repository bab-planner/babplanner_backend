-- 관측 출처 정보.
--
-- V22는 congestion_status에 있던 현재값을 옮겨 담느라 어느 장비가 언제 어떤
-- 모델로 판정했는지를 담을 자리가 없었다. 이력을 나중에 감사하려면 그 두
-- 가지가 필요하다. 어느 카메라가 이상한 값을 보내고 있는지, 어느 모델
-- 버전부터 값이 튀는지는 이 두 열이 없으면 알 수 없다.
--
-- V22가 옮겨 둔 기존 행에는 넣을 값이 없다. 지어내지 않고 NULL로 둔다.
-- 새로 들어오는 수집 요청은 device_id를 반드시 보낸다.
-- 한 문장에 두 열을 붙이는 문법은 엔진마다 다르다. 나눠 쓴다.
ALTER TABLE congestion_observations ADD COLUMN device_id VARCHAR(100) NULL;
ALTER TABLE congestion_observations ADD COLUMN model_version VARCHAR(100) NULL;
