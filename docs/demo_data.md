# 관리자 웹 시연 데이터 (Demo Data Seeder)

시연용 가상 데이터를 **기존 데이터를 건드리지 않고** 채웠다가 **그 데이터만** 지울 수 있다.
local/dev 프로필 전용이며 production에서는 켜면 기동이 실패한다.

## 왜 화면이 비어 있었나

| 화면 | 집계 근거 | 비어 있던 이유 |
|---|---|---|
| 대시보드 혼잡도 | `congestion_status` 사이트별 1행 (`CongestionService.findCurrent`) | V29가 개발 샘플 현재값을 지웠고, 쓰는 주체는 CCTV 수집 API(`/internal/v1/congestion-observations`)뿐 |
| 혼잡도 시계열 | `congestion_observations` 기간 조회 (`AdminCongestionService`) | 같은 이유로 관측 이력이 없음 |
| 대시보드 잔반 요약 | **오늘 제공 메뉴**에 연결된, 검토 완료(APPROVED/REJECTED) 제출 건수 + APPROVED 중 `waste_rate`가 있는 건의 평균 (`AdminWasteService`) | 제출이 없고, 수동 승인은 `waste_rate`를 기록하지 않아 평균은 항상 null |
| 메뉴별 잔반률 | APPROVED 이면서 `waste_rate`가 있는 건만 (`aggregateWasteRateByMenu`) | 수동 검토로는 값이 생기지 않아 빈 배열 |
| 설문 결과 | `survey_responses`/`survey_answers` | 응답 없음 |
| 식단/잔반 검토 | 메뉴 · 제출 | 고정 날짜(2026-08-26) 샘플만 있고 최근 7일 식단·제출이 없음 |

## 실행 방법

환경변수(`/opt/bapplanner/.env`)로 **한 번에 하나**의 명령만 지정한다.

| 변수 | 기본 | 설명 |
|---|---|---|
| `BAPPLANNER_DEMO_DATA_ENABLED` | `false` | `true` + `dev`/`local` 프로필일 때만 실행. 다른 프로필에서 `true`면 기동 실패 |
| `BAPPLANNER_DEMO_DATA_ACTION` | (없음) | `seed`(생성) 또는 `cleanup`(정리). 비우거나 다른 값이면 기동 실패 |
| `BAPPLANNER_DEMO_DATA_DRY_RUN` | `false` | `true`면 같은 계산을 하고 롤백한다. 이미지 파일도 만들지 않는다. 결과 건수는 로그에 남는다 |

앱이 기동한 뒤 한 번 실행되고 결과가 로그에 남는다(`시연 데이터 작업 결과: ...`).
작업이 끝나면 `ENABLED=false`로 되돌려 재기동한다. 켜 둔 채 재시작해도 seed는 중복을 만들지 않는다.

## 만들어지는 데이터 (최근 7일, 급식 운영 시간대 기준)

날짜·시각은 `Clock`의 시간대(`BAPPLANNER_BUSINESS_ZONE_ID`, 운영 필수값이며 `Asia/Seoul`)로 계산한다. 코드에 시간대를 박지 않는다(`BusinessTimeZoneTest` 규칙).

| 종류 | 건수 | 내용 |
|---|---|---|
| 가상 사용자 | 20 | 학번 `DEMO-001`~`DEMO-020`, 이름 `시연학생NN`, 전화 `0100000NNNN`. 로그인 불가(무작위 비밀번호를 BCrypt로 해시 후 폐기), role=USER |
| 식단 | 최대 49 | 7일 × (첫 식당: 조식1·중식3·석식1, 그 외 식당: 중식1·석식1). 끼니에 이미 식단이 있으면 그 끼니는 건드리지 않는다(DevMenuSeeder 포함) |
| 혼잡도 관측 | 식당당 일 19건 | 11:00~14:00 10분 간격. 여유→보통→혼잡→보통→여유. 출처 `DEMO_SEED`, 모델 버전 없음. 현재 시각 이후는 만들지 않음 |
| 혼잡도 현재값 | 식당당 1 | 가장 최근 시연 관측과 같은 값. 실제 수집기(`CCTV_AI`) 값이 있으면 덮어쓰지 않음 |
| 잔반 제출 | 약 80~120 | 중식 메뉴 대상. 대기/승인/반려 혼합, 오늘도 검토 완료 기록 포함. 샘플 PNG(도형만, 사람·글자 없음)를 실제 이미지 저장소에 저장 |
| 시연 잔반률 | 승인 건의 약 75% | `analysis_model_version='DEMO_SEED'`, `confidence`는 null. 실제 AI 결과가 아니다 |
| 리워드 | 승인 건당 1회 | 기존 `RewardService.creditOnce` 사용(출처=제출 id, unique). 계정 잔액 = 원장 합계 |
| 설문 | 2 | `[시연] 학식 만족도 조사`(4문항), `[시연] 식당 이용 환경 조사`(3문항). 응답 38건, 이미 종료된 설문이라 앱 사용자에게는 노출되지 않음 |

## 소유권과 멱등성

- 새 테이블 `demo_seed_records(entity_type, entity_id)`(V30)에 시더가 만든 모든 행을 등록한다. 정리는 이 기록에 있는 행만 지운다.
- 식별 키로 중복을 막는다: 끼니(식당·날짜·끼니), 관측(식당·시각·출처), 제출(사용자·메뉴), 설문(제목), 응답(설문·사용자), 사용자(전화/학번).
- 사용자 배정은 날짜에서 계산하므로 7일 창이 밀려도 같은 날짜는 같은 결과가 된다.
- 삽입 전에 검증한다: 가상 사용자 전화/학번이 시연 데이터가 아닌 기존 사용자와 충돌, 같은 제목의 기존 설문, 관리자 계정 없음, 활성 식당 없음이면 **아무것도 쓰지 않고 중단**한다.
- 전체가 한 트랜잭션이다. 실패하면 롤백되고 이미 저장한 샘플 이미지도 지운다.
- 기존 관리자 계정·메뉴·사용자는 수정하지 않는다. `DROP`/`TRUNCATE`/무조건 `DELETE`/Flyway 수정/볼륨 삭제는 없다.

## 정리 (cleanup)

`BAPPLANNER_DEMO_DATA_ACTION=cleanup` 으로 재기동하면 등록된 행만 지운다.

- 먼저 `BAPPLANNER_DEMO_DATA_DRY_RUN=true`로 건수를 확인할 수 있다.
- 다른 행이 아직 참조하는 시연 행(예: 실제 제출이 가리키는 시연 메뉴)은 남긴다.
- 잔반 샘플 이미지 파일은 DB 삭제가 커밋된 뒤에 지운다.
- 여러 번 실행해도 안전하다.

## 확인용 SQL (읽기 전용)

```sql
SELECT entity_type, COUNT(*) FROM demo_seed_records GROUP BY entity_type;
SELECT COUNT(*) FROM users WHERE student_id LIKE 'DEMO-%';
SELECT status, COUNT(*) FROM waste_submissions GROUP BY status;
SELECT site_id, source, COUNT(*), MAX(observed_at) FROM congestion_observations GROUP BY site_id, source;
SELECT s.title, COUNT(r.id) FROM surveys s LEFT JOIN survey_responses r ON r.survey_id = s.id GROUP BY s.id;
```
