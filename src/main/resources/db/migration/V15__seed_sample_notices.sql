-- 개발/테스트용 공지 fixture. 실제 학교 운영 공지가 아니다.
-- 문자열 연결은 CONCAT을 쓴다. MySQL에서 ||는 논리 OR이라 이식되지 않는다.
-- 정렬 검증을 위해 important와 published_at을 서로 다르게 둔다.
INSERT INTO notices (title, content, important, published_at, created_at, updated_at) VALUES
    ('[개발 샘플] 시스템 점검 안내',
     CONCAT('샘플 공지입니다.', CHAR(10), CHAR(10), '두 번째 문단입니다. 줄바꿈이 보존되는지 확인하는 fixture입니다.'),
     TRUE,
     TIMESTAMP '2026-08-20 01:00:00', TIMESTAMP '2026-08-20 01:00:00', TIMESTAMP '2026-08-20 01:00:00'),
    ('[개발 샘플] 중요 공지 두 번째',
     '중요 표시가 붙은 두 번째 샘플 공지입니다.',
     TRUE,
     TIMESTAMP '2026-08-24 01:00:00', TIMESTAMP '2026-08-24 01:00:00', TIMESTAMP '2026-08-24 01:00:00'),
    ('[개발 샘플] 일반 공지 최신',
     '중요 표시가 없는 일반 샘플 공지입니다.',
     FALSE,
     TIMESTAMP '2026-08-25 01:00:00', TIMESTAMP '2026-08-25 01:00:00', TIMESTAMP '2026-08-25 01:00:00'),
    ('[개발 샘플] 일반 공지 이전',
     '중요 표시가 없는 더 오래된 샘플 공지입니다.',
     FALSE,
     TIMESTAMP '2026-08-18 01:00:00', TIMESTAMP '2026-08-18 01:00:00', TIMESTAMP '2026-08-18 01:00:00');
