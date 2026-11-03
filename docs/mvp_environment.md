# 밥플래너 MVP 환경 설정

네 구성 요소가 각각 무엇을 요구하는지 한 곳에 모았다. 값은 전부 실제 소스에서
읽은 것이고, 여기에 실제 비밀 값은 적지 않는다.

```
Flutter App ─┐
             ├─→ Spring Backend ─→ MySQL 8.4
Admin Web ───┘         │
                       └────────→ FastAPI (AI)
```

FastAPI와 MySQL은 브라우저나 앱이 직접 부르지 않는다. 백엔드만 접근한다.

---

## 1. Spring Backend

`src/main/resources/application.yml` 에 정의된 것 전부다.

| 변수 | 필수 | local 예시 | production | 비밀 | 비고 |
|---|---|---|---|---|---|
| `DB_URL` | 예 | `jdbc:mysql://127.0.0.1:3307/babplanner?serverTimezone=UTC&characterEncoding=UTF-8` | 운영 DB | 아니오 | 기본값 없음. 없으면 기동 실패 |
| `DB_USERNAME` | 예 | — | — | 예 | |
| `DB_PASSWORD` | 예 | — | — | **예** | |
| `JWT_SIGNING_KEY` | 예 | 임의 32바이트 이상 | 별도 관리 | **예** | 32바이트 미만이면 기동 실패 |
| `PHONE_VERIFICATION_HMAC_KEY` | 예 | 임의 32바이트 이상 | 별도 관리 | **예** | `JWT_SIGNING_KEY`와 **다른 값**이어야 한다 |
| `BAPPLANNER_BUSINESS_ZONE_ID` | 예 | `Asia/Seoul` | `Asia/Seoul` | 아니오 | 급식 운영일을 끊는 IANA 시간대. 미설정/오타면 기동 실패 |
| `BAPPLANNER_INTERNAL_SERVICE_TOKEN` | 예 | 임의 32자 이상 | 별도 관리 | **예** | `/internal/v1/**` 이 **받는** 토큰 |
| `AI_SERVICE_BASE_URL` | 조건부 | `http://127.0.0.1:8001` | 내부 주소 | 아니오 | 다음 영업일 점심 식수 예측(`/admin/demand-forecasts/*`)에 필요 |
| `AI_SERVICE_TOKEN` | 조건부 | 임의 32자 이상 | 별도 관리 | **예** | Spring이 FastAPI로 **보내는** 토큰 |
| `SMS_MODE` | 예 | `noop` | 실제 어댑터 | 아니오 | 기본값 없음. 운영에서 `noop`이 조용히 선택되지 않게. **`noop`이면 send-code 응답에 `developmentCode`가 포함되므로 공개 환경에서 절대 쓰지 말 것** (docs/account_deletion.md) |
| `BAPPLANNER_CORS_ALLOWED_ORIGINS` | 아니오 | 비움 | 관리자 웹 Origin | 아니오 | 쉼표 구분, 정확히 일치하는 `https://host[:port]` 만 허용. 와일드카드/http/경로/끝 슬래시는 **기동 실패**. 기본은 빈 목록(브라우저 cross-origin 전부 거절) |
| `BAPPLANNER_DEMO_DISABLE_SIGNUP` | 아니오 | `false` | **`true`** (공개 시연) | 아니오 | `true`면 `POST /api/v1/auth/phone/send-code`, `/phone/verify`, `/signup` 이 403(`FORBIDDEN`). 기본 `false`(기존 동작). **`SMS_MODE=noop` 으로 공개 배포하면 반드시 `true`** |
| `WASTE_STORAGE_MODE` | 예 | `local` | (아래 참고) | 아니오 | 기본값 없음 |
| `WASTE_LOCAL_ROOT` | 조건부 | 로컬 디렉터리 | — | 아니오 | `storage-mode=local` 일 때만 |
| `REWARD_WASTE_APPROVAL_POINTS` | 아니오 | `100` | `100` | 아니오 | 관리자가 잔반 인증을 APPROVED로 바꿀 때 지급 포인트(`bapplanner.reward.waste-approval-points`). 0 이상 정수, 음수면 기동 실패. 제출 한 건당 한 번만 지급(DB unique). 쿠폰 발급 규칙은 미정이라 `couponIssued`는 항상 false |
| `DEV_ADMIN_ENABLED` | 아니오 | `true` | **false** | 아니오 | 기본 `false` |
| `DEV_ADMIN_PHONE` / `DEV_ADMIN_PASSWORD` / `DEV_ADMIN_STUDENT_ID` / `DEV_ADMIN_NAME` | 조건부 | — | 사용 안 함 | **예**(비밀번호) | `DEV_ADMIN_ENABLED=true` 일 때만. `local`/`dev` 프로필 전용 |
| `DEV_USER_ENABLED` | 아니오 | `true` | **false** | 아니오 | 기본 `false` |
| `DEV_USER_PHONE` / `DEV_USER_PASSWORD` / `DEV_USER_STUDENT_ID` / `DEV_USER_NAME` | 조건부 | — | 사용 안 함 | **예**(비밀번호) | 위와 같다 |
| `DEV_SURVEY_ENABLED` | 아니오 | `true` | **false** | 아니오 | 기본 `false` |

### 없어진 이름

Stage 24에서 **제거**됐다. 지금 코드는 이 이름을 읽지 않는다.

| 옛 이름 | 새 이름 |
|---|---|
| `AI_WASTE_BASE_URL` | (삭제됨) |
| `AI_WASTE_SERVICE_TOKEN` | (삭제됨) |

옛 이름을 설정해도 무시된다. 폴백이 없으므로 배포 스크립트를 반드시 고쳐야 한다.

### 두 서비스 토큰은 다른 것이다

| | 방향 | 쓰는 곳 |
|---|---|---|
| `BAPPLANNER_INTERNAL_SERVICE_TOKEN` | Spring이 **받는다** | CCTV 수집기 등이 `/internal/v1/congestion-observations` 를 부를 때 |
| `AI_SERVICE_TOKEN` | Spring이 **보낸다** | Spring이 FastAPI의 `/internal/v1/ai/**` 를 부를 때 |

같은 값으로 둘 필요가 없고, 그러지 않는 편이 낫다. 하나가 새면 양쪽이 함께
뚫린다.

### 시간대를 반드시 설정해야 하는 이유

`Menu.serviceDate`는 시각이 아니라 급식소 현지 영업일이다. 시간대가 없으면
UTC로 끊게 되고, 한국에서는 자정부터 오전 9시까지 대시보드가 전날 급식을
오늘로 보여 준다. 그래서 미설정을 기동 실패로 막아 두었다. 이건 의도된
동작이다.

---

## 2. FastAPI (AI)

`src/babplanner_ai/config.py` 에 정의된 것 전부다.

| 변수 | 필수 | local 예시 | production | 비밀 | 비고 |
|---|---|---|---|---|---|
| `APP_ENV` | 예 | `local` | `production` | 아니오 | 기본값 없음 |
| `AI_SERVICE_TOKEN` | 예 | Spring과 **같은 값** | 별도 관리 | **예** | 32자 미만이면 기동 실패 |
| `DEMAND_FORECAST_MODE` | 아니오 | 비움(`strict`) | **`poc`** | 아니오 | 합성 PoC 식수 예측 모델을 production에서 서비스하려면 명시해야 한다. 기본 `strict`는 production에서 합성 모델을 막는다(status unavailable, predict 503) |
| `DEMAND_FORECAST_MODEL_PATH` | 아니오 | 비움 | 마운트 경로 | 아니오 | 식수 예측 모델 artifact. 기본은 저장소의 `research/demand_forecasting/models/` |
| `DEMAND_FORECAST_METADATA_PATH` | 아니오 | 비움 | 마운트 경로 | 아니오 | 위 artifact의 metadata.json |
| `DEMAND_HISTORY_PATH` | 아니오 | 비움 | 마운트 경로 | 아니오 | 과거 식수 이력(지금은 합성 CSV) |
| `DEMAND_CCTV_LOG_PATH` | 아니오 | 비움 | 마운트 경로 | 아니오 | 과거 CCTV 이력(지금은 합성 CSV) |

AI 서비스는 **다음 영업일 점심 식수 예측**(`/internal/v1/ai/demand-forecast/*`) 하나만
제공한다. 잔반 분석, 일반 수요 예측, 발주 추천 endpoint와 그 모드 설정
(`WASTE_MODEL_MODE`, `DEMAND_MODEL_MODE`, `PROCUREMENT_MODEL_MODE`)은 Spring이 더 이상 쓰지
않는다. (혼잡도는 CCTV 수집기가 `/internal/v1/congestion-observations`로 직접 보낸다.)
식수 예측은 저장된 모델 artifact로 동작한다. 파일이 없으면 FastAPI는 뜨고 식수 예측만 503이다. 합성데이터
모델은 `APP_ENV=production`에서 로드하지 않는다. Spring 쪽 설정은 위의
`AI_SERVICE_BASE_URL`/`AI_SERVICE_TOKEN` 그대로다. 연동 전체는
[demand_forecast_poc.md](demand_forecast_poc.md).

---

## 3. Flutter 사용자 앱

`lib/core/network/api_config.dart` 에 정의된 것 전부다. `--dart-define` 으로 넣는다.

| 키 | 필수 | 값 | 비밀 | 비고 |
|---|---|---|---|---|
| `APP_DATA_MODE` | 아니오 | `real` 일 때만 실제 서버를 본다 | 아니오 | 기본 `fake` |
| `API_BASE_URL` | 조건부 | `http://10.0.2.2:8080` (에뮬레이터) | 아니오 | 비어 있으면 Real transport를 만들 수 없어 Fake로 동작 |

앱에는 서버 비밀 값이 하나도 들어가지 않는다. 설치본을 뜯으면 `--dart-define`
값이 그대로 보이므로, 토큰류를 여기 넣어서는 안 된다.

---

## 4. Admin Web

`.env.example` 과 `vite.config.ts` 에 정의된 것 전부다.

| 키 | 필수 | local | production | 비밀 | 비고 |
|---|---|---|---|---|---|
| `VITE_API_BASE_URL` | 아니오 | 비움 | 비움(같은 출처) | 아니오 | 비면 상대 경로를 쓴다 |
| `VITE_DEV_API_TARGET` | 아니오 | `http://127.0.0.1:8080` | 사용 안 함 | 아니오 | 개발 서버 프록시 대상 |

**`VITE_*` 는 전부 브라우저 번들에 그대로 실린다.** 서비스 토큰, JWT 서명 키,
DB 비밀번호를 여기 넣으면 누구나 읽을 수 있다.

---

## 5. 배포 형태 (권장)

호스팅 업체는 아직 정해지지 않았다. 아래는 MVP 권장안이고, 실제 결정이 나면
그때 확정한다.

```
https://admin.example
  /        → Admin 정적 파일
  /api/    → Spring Backend
```

같은 출처 리버스 프록시도 가능하다. 하지만 현재 시연은 **Vercel(관리자 웹) +
Naver Cloud(백엔드)** 별도 출처이므로 아래 6절의 CORS 설정이 필요하다.

- FastAPI는 브라우저를 향한 서비스가 아니다. 공개 API로 노출하지 않는다.
- MySQL은 백엔드만 접근한다. 앱·관리자 웹·AI 서비스는 DB에 직접 붙지 않는다.
- AI 서비스는 상태를 갖지 않는다. 저장은 전부 백엔드가 한다.

---

## 6. Vercel 관리자 웹 ↔ Naver Cloud 백엔드 (CORS)

```
브라우저 ─ https://<admin>.vercel.app  (Vercel, 정적 관리자 웹)
   │  fetch + Authorization: Bearer
   ▼
https://api.<도메인>  (Caddy: TLS 종료) ─→ spring:8080 (외부 비공개) ─→ MySQL
```

- HTTPS는 Caddy 같은 리버스 프록시가 처리한다. Spring은 8080을 외부에 열지 않고
  프록시만 접근한다. 관리자 웹이 https이므로 API도 반드시 https여야 한다(mixed content).
- 인증은 Bearer JWT다. 쿠키를 쓰지 않으므로 `Access-Control-Allow-Credentials` 는 보내지 않는다.

### CORS 동작

| 항목 | 값 |
|---|---|
| 적용 경로 | `/api/v1/**` 만. `/internal/v1/**` 는 브라우저 CORS 대상이 아니다 |
| 허용 Origin | `BAPPLANNER_CORS_ALLOWED_ORIGINS` 와 **정확히** 일치하는 것만. 와일드카드·Origin 반사 없음 |
| 메서드 | GET, POST, PUT, PATCH, DELETE, OPTIONS |
| 요청 헤더 | Authorization, Content-Type, Accept |
| preflight | 허용 Origin이면 인증 없이 처리. 미허용 Origin은 403, CORS 헤더 없음 |
| Origin 헤더 없는 요청 | 영향 없음(앱, 수집기, curl) |

Vercel 프리뷰 URL(`*.vercel.app` 해시 배포)은 와일드카드를 쓸 수 없으므로 허용되지 않는다.
쓰려면 그 URL을 정확히 추가한다. 운영은 고정 도메인 하나를 권장한다.

**CORS는 접근 통제가 아니다.** curl 같은 비브라우저 클라이언트는 CORS를 무시한다.
접근 통제는 JWT + ADMIN 역할 검사가 하고, 공개 시연에서는 아래 가입 차단이 추가로 필요하다.

### 공개 시연 보안: `BAPPLANNER_DEMO_DISABLE_SIGNUP=true` 필수

`SMS_MODE=noop` 이면 send-code 응답에 인증번호(`developmentCode`)가 실려, 누구나
가입할 수 있다. 공개 배포에서는 `BAPPLANNER_DEMO_DISABLE_SIGNUP=true` 로 세 경로
(`send-code`, `verify`, `signup`)를 서버에서 막는다. `login`, `refresh` 와 기존
관리자 계정은 그대로 동작한다. 설정을 빠뜨리면 기존 동작(가입 허용)이므로 **공개
배포 체크리스트에 반드시 포함**한다.

### Naver Cloud `/opt/bapplanner/.env` 에 필요한 값

값은 서버에서만 채우고 저장소에 커밋하지 않는다.

```
DB_URL=...                         # 기존 값 유지
DB_USERNAME=...
DB_PASSWORD=...
JWT_SIGNING_KEY=...                # 32바이트 이상
PHONE_VERIFICATION_HMAC_KEY=...    # JWT 키와 다른 값
BAPPLANNER_BUSINESS_ZONE_ID=Asia/Seoul
BAPPLANNER_INTERNAL_SERVICE_TOKEN=...
SMS_MODE=noop
SPRING_PROFILES_ACTIVE=dev         # 기존 배포 그대로
# --- 이번에 추가 ---
BAPPLANNER_CORS_ALLOWED_ORIGINS=https://<관리자>.vercel.app
BAPPLANNER_DEMO_DISABLE_SIGNUP=true
```

### Vercel 쪽

- 환경변수 `VITE_API_BASE_URL=https://api.<도메인>` (비밀 값 아님. `VITE_*` 는 번들에 노출된다).
- 관리자 웹은 `/api/v1/**` 를 이 base URL 뒤에 붙여 호출한다. 값 변경 후 재배포한다.

### 확인

```
# 허용 Origin: 200 + access-control-allow-origin 이 Origin과 동일
curl -i -X OPTIONS https://api.<도메인>/api/v1/auth/login   -H 'Origin: https://<관리자>.vercel.app'   -H 'Access-Control-Request-Method: POST'   -H 'Access-Control-Request-Headers: authorization,content-type'
# 미허용 Origin: 403, access-control-allow-origin 없음
# 가입 차단: POST /api/v1/auth/signup → 403 FORBIDDEN
```
