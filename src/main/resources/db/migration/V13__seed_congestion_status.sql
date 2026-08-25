-- 개발용 초기 상태. CCTV/AI 판정 결과가 아니다.
-- level 문자열은 OpenAPI CongestionLevel enum과 정확히 일치해야 한다.
INSERT INTO congestion_status (site_id, level, people_count, display_message, source,
                               observed_at, updated_at)
SELECT s.id, 'NORMAL', 42, '5분 이내', 'DEV_SEED',
       TIMESTAMP '2026-08-26 02:30:00', TIMESTAMP '2026-08-26 02:30:00'
FROM dining_sites s WHERE s.code = 'STUDENT';

INSERT INTO congestion_status (site_id, level, people_count, display_message, source,
                               observed_at, updated_at)
SELECT s.id, 'COMFORTABLE', 8, NULL, 'DEV_SEED',
       TIMESTAMP '2026-08-26 02:30:00', TIMESTAMP '2026-08-26 02:30:00'
FROM dining_sites s WHERE s.code = 'STAFF';
