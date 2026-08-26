-- 어떤 분석 구현이 이 판정을 냈는지 남긴다. 내부 추적용이며 사용자 API로
-- 나가지 않는다. 분석 전이나 실패 건은 알 수 없으므로 nullable이다.
ALTER TABLE waste_submissions
    ADD COLUMN analysis_model_version VARCHAR(100) NULL;
