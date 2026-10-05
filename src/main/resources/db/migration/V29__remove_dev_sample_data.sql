-- 개발/테스트용 샘플 데이터 정리. V13(혼잡도), V15(공지)가 넣은 행 중 "정확히 그 값 그대로"인
-- 것만 지운다. 샘플 행을 관리자가 고쳤거나(updated_at 변경), 수집기가 갱신했거나(source,
-- observed_at 변경), 같은 제목이라도 다른 시각에 만든 행은 조건에 맞지 않아 남는다.
-- 해당하는 행이 없는 DB에서는 아무 일도 일어나지 않는다. 사용자 데이터는 건드리지 않는다.
--
-- 식단(V11)은 지우지 않는다. 식단은 잔반 제출, 메뉴 구성, 알레르기, 수요 예측 등이 id로
-- 참조할 수 있고, 같은 이름의 실제 식단과 구분할 확실한 표식이 없다.
-- 식당(V8)과 알레르기(V5)는 실제 기준 데이터라 지우지 않는다.

-- V22가 congestion_status를 백필한 관측 이력. 수집기가 보낸 행은 device_id가 있다.
DELETE FROM congestion_observations
WHERE source = 'DEV_SEED'
  AND observed_at = TIMESTAMP '2026-08-26 02:30:00'
  AND created_at = TIMESTAMP '2026-08-26 02:30:00'
  AND device_id IS NULL;

-- V13 현재 혼잡도(학생식당 NORMAL/42명, 교직원식당 COMFORTABLE/8명).
DELETE FROM congestion_status
WHERE source = 'DEV_SEED'
  AND observed_at = TIMESTAMP '2026-08-26 02:30:00'
  AND updated_at = TIMESTAMP '2026-08-26 02:30:00'
  AND site_id IN (SELECT id FROM dining_sites WHERE code IN ('STUDENT', 'STAFF'));

-- V15 공지 4건. 제목과 생성/수정 시각이 시드 값과 모두 같을 때만.
DELETE FROM notices
WHERE (title = '[개발 샘플] 시스템 점검 안내'
       AND created_at = TIMESTAMP '2026-08-20 01:00:00'
       AND updated_at = TIMESTAMP '2026-08-20 01:00:00')
   OR (title = '[개발 샘플] 중요 공지 두 번째'
       AND created_at = TIMESTAMP '2026-08-24 01:00:00'
       AND updated_at = TIMESTAMP '2026-08-24 01:00:00')
   OR (title = '[개발 샘플] 일반 공지 최신'
       AND created_at = TIMESTAMP '2026-08-25 01:00:00'
       AND updated_at = TIMESTAMP '2026-08-25 01:00:00')
   OR (title = '[개발 샘플] 일반 공지 이전'
       AND created_at = TIMESTAMP '2026-08-18 01:00:00'
       AND updated_at = TIMESTAMP '2026-08-18 01:00:00');
