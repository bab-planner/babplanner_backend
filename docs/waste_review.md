# 잔반 인증 수동 검토

AI 분석은 사용하지 않는다. 백엔드에는 분석기, 비동기 처리, 복구 러너가 없다(`WASTE_ANALYZER_MODE` 설정도 사라졌다).

## 흐름

1. 사용자 `POST /api/v1/waste-submissions` → 이미지 저장 후 **PENDING**으로 생성 (201, `earnedPoints`/`couponIssued` = null).
2. 관리자가 관리자 웹에서 사진을 보고 승인/반려한다.
3. **APPROVED로 처음 바뀔 때만** `bapplanner.reward.waste-approval-points`(기본 100)를 지급한다(원장 `WASTE_APPROVED`, 출처 `WASTE_SUBMISSION`+제출 id가 DB unique). REJECTED는 지급 없음.
4. 사용자 `GET /api/v1/waste-submissions/{id}`: PENDING → `earnedPoints` null / `couponIssued` null, APPROVED → 실제 지급액(100) / false, REJECTED → 0 / false.

규칙: APPROVED는 종료 상태(반려 불가, 포인트 회수 없음). REJECTED는 나중에 승인할 수 있다. 승인 재호출은 멱등(추가 지급 없음). 동시 승인은 조건부 UPDATE(행 잠금)와 원장 unique 제약으로 한 번만 지급된다. 쿠폰 발급 규칙은 미정이라 `couponIssued`는 항상 false.

기존 상태값 RECEIVED/VERIFYING/ANALYZING/FAILED는 예전 행을 읽기 위해 남겼다. 이 상태의 행도 승인/반려할 수 있다.

## 관리자 API (ROLE_ADMIN 전용, 일반 사용자 403, 익명 401)

- `GET /api/v1/admin/waste-submissions?status=PENDING|APPROVED|REJECTED&limit=50` (limit 1~200, 기본 50, 최신순)
- `GET /api/v1/admin/waste-submissions/{id}/image` — 이미지 바이트. `Content-Type`은 바이트(JPEG/PNG/GIF/WebP/HEIC)로 판별하며 이미지가 아니면 422, 파일이 없으면 404. `Cache-Control: private, no-store`. Authorization 헤더가 필요하므로 관리자 웹은 fetch로 blob을 받아 표시한다.
- `POST /api/v1/admin/waste-submissions/{id}/approve`, `.../reject` (본문 없음) → 200, 목록 항목과 같은 JSON. 없는 id 404, 승인된 건 반려 409 `WASTE_SUBMISSION_ALREADY_APPROVED`.

항목 JSON:

```json
{"id":1,"userName":"홍길동","userPhoneMasked":"010-****-5678","menuId":3,"menuName":"제육덮밥 정식",
 "serviceDate":"2026-08-26","status":"PENDING","earnedPoints":null,"couponIssued":null,
 "createdAt":"2026-08-26T03:00:00Z","capturedAt":"2026-08-26T03:00:00Z","reviewedAt":null}
```

검토자와 시각은 `waste_submissions.reviewed_by_user_id`(FK 없음), `reviewed_at`(V28)에 남는다.

## 잔반 집계 (`/admin/waste/summary`, `/admin/waste/by-menu`)

응답 모양은 그대로다. `totalSubmissions`는 기간 내 검토된(APPROVED+REJECTED) 제출 수, `averageWasteRate`는 잔반률이 기록된 APPROVED 건의 평균(수동 검토에서는 기록되지 않으므로 null), by-menu는 잔반률 기록 건만 집계하므로 비어 있다.

## 샘플 데이터 정리 (V29)

운영 DB에서 아래 조건과 **정확히 일치하는 행만** 지운다. 조건에 맞는 행이 없으면 아무 일도 없다.

- `congestion_status`: `source='DEV_SEED'` 이고 `observed_at`=`updated_at`=`2026-08-26 02:30:00`, 사이트 코드 STUDENT/STAFF (V13).
- `congestion_observations`: `source='DEV_SEED'`, `observed_at`=`created_at`=`2026-08-26 02:30:00`, `device_id IS NULL` (V22 백필).
- `notices`: 제목이 `[개발 샘플] 시스템 점검 안내`(2026-08-20 01:00:00) / `[개발 샘플] 중요 공지 두 번째`(2026-08-24 01:00:00) / `[개발 샘플] 일반 공지 최신`(2026-08-25 01:00:00) / `[개발 샘플] 일반 공지 이전`(2026-08-18 01:00:00) 이면서 `created_at`=`updated_at`=그 시각 (V15). 관리자가 수정했거나(updated_at 변경) 시각이 다르면 남는다.

**지우지 않는 것**: 식단(V11) — 잔반 제출, 구성 메뉴, 알레르기, 수요 예측 등이 id로 참조할 수 있고 같은 이름의 실제 식단과 구분할 확실한 표식이 없다. 식당(V8), 알레르기(V5), 사용자 데이터, 관리자가 입력한 모든 데이터. 필요하면 관리자 식단 화면에서 지운다(제출이 있으면 `MENU_IN_USE`로 보호된다).

정리 후 수집기가 값을 보내기 전까지 `GET /api/v1/congestion/current`는 데이터가 없어 404(`CONGESTION_NOT_AVAILABLE`)다.
