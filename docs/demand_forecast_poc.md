# 식수 예측 PoC 연동 (Spring ↔ FastAPI)

관리자 화면이 Spring을 부르면, Spring이 내부 FastAPI AI 서버를 불러 다음 날
중식 식수 예측을 돌려준다.

> **지금 모델은 합성데이터로 학습한 PoC다.** 응답의 `model.syntheticData: true`와
> `disclaimer`가 그것을 알린다. 이 숫자로 식재료를 주문하면 안 된다. 실제 운영
> 데이터로 학습한 모델로 교체하기 전까지 이 값은 시연용이다.

## 구조

```
Admin Web
   ↓  POST /api/v1/admin/demand-forecasts/predict   (관리자 JWT)
Spring Boot 메인 백엔드
   ↓  POST /internal/v1/ai/demand-forecast/predict  (X-Service-Token)
FastAPI AI 추론 서버
   ↓
식수 예측 모델 artifact (joblib)
```

- **프론트엔드는 FastAPI를 직접 부르지 않는다.** FastAPI 주소와 토큰은 Spring만 안다.
- 관리자 인증·권한은 Spring이 맡는다(`/api/v1/admin/**` → ADMIN). FastAPI에는 사용자
  인증이 없고, 서비스 토큰으로 호출자가 Spring인지만 확인한다.
- Spring은 모델을 모른다. 무엇을 보냈는지, 몇 명이 나왔는지, 어느 모델(version)이
  냈는지, 합성데이터 모델인지, 경고가 있는지만 다룬다.
- AI 서비스가 제공하는 유일한 예측 기능이다. 예전의 일반 수요 예측과 발주 추천은
  제거되었다. 이 기능은 결과를 저장하지 않는다.

## 관리자 API

### `POST /api/v1/admin/demand-forecasts/predict`

```json
{
  "date": "2026-09-15",
  "semester": 2,
  "semesterWeek": 3,
  "examPeriod": false,
  "holiday": false,
  "campusEvent": false,
  "temperature": 24.0,
  "rainfall": 0.0,
  "humidity": 60.0,
  "menuCategory": "WESTERN",
  "mainMenu": "돈까스",
  "specialMenu": false
}
```

`menuCategory`는 다른 메뉴 API와 같은 enum 이름이다(`KOREAN`, `CHINESE`, `WESTERN`,
`JAPANESE`, `SNACK`, `OTHER`). Spring이 FastAPI로 보낼 때 한글 라벨(한식·중식·양식·
일식·분식·기타)로 바꾼다.

| 검증 | 실패 시 |
|---|---|
| 모든 필드 필수 | 400 `VALIDATION_ERROR` |
| `date` 평일 | 400, `details.fields.dateOnWeekday` |
| `semester` 1~2, `semesterWeek` 1~16 | 400 |
| `temperature` -30~45, `rainfall` 0~500, `humidity` 0~100 | 400 |
| `mainMenu` 1~50자 | 400 |
| `menuCategory` enum 이름 | 400 (한글 라벨도 거절) |

범위는 FastAPI와 같게 두었다. 최종 판단은 FastAPI 계약이 한다.

응답 (`ApiResponse` 봉투, 200):

```json
{
  "success": true,
  "data": {
    "forecastDate": "2026-09-15",
    "mealType": "LUNCH",
    "predictedMealCount": 431,
    "reference": {
      "previousDay": 350,
      "sameWeekdayPreviousWeek": 375,
      "rolling7DayAverage": 367,
      "historyAsOf": "2026-09-11"
    },
    "conditions": {
      "weekday": "화요일", "semester": 2, "semesterWeek": 3,
      "examPeriod": false, "holiday": false, "campusEvent": false,
      "temperature": 24.0, "rainfall": 0.0, "humidity": 60.0,
      "menuCategory": "WESTERN", "mainMenu": "돈까스",
      "specialMenu": false
    },
    "model": {
      "name": "lightgbm",
      "version": "54453980807e",
      "syntheticData": true,
      "trainedAt": "2026-09-12T23:43:37"
    },
    "disclaimer": "합성데이터 기반 PoC 예측값이며 실제 급식 운영 정확도를 의미하지 않습니다.",
    "warnings": ["직전 평일(2026-09-14) 기록이 이력에 없어 가장 최근 기록(2026-09-11)을 기준으로 과거 feature를 만들었습니다."],
    "predictedAt": "2026-09-12T16:20:00.123Z"
  },
  "error": null
}
```

- `conditions`는 관리자가 보낸 값이고, `weekday`만 FastAPI가 날짜에서 계산한 값이다.
- 예측 구간(95% 등)은 없다. FastAPI에 보정된 구간이 없다.
- 나중에 예측을 DB에 저장한다면 `forecastDate`, `predictedMealCount`, `model.version`,
  `model.syntheticData`, `predictedAt`(→ createdAt)을 함께 남긴다. 그래서 지금도 응답에서
  이 값들을 버리지 않는다.

### `GET /api/v1/admin/demand-forecasts/status`

```json
{"success": true, "data": {"available": true, "modelLoaded": true, "historyAvailable": true,
  "modelName": "lightgbm", "modelVersion": "54453980807e", "syntheticData": true,
  "trainingPeriod": ["2023-03-13", "2026-09-11"], "reason": null, "warnings": []}, "error": null}
```

FastAPI에 닿았는데 모델이 없으면 200 + `available: false` + `reason`
(예: `model artifact not found`). FastAPI에 닿지 못하면 503이다.

## 오류 처리

**예측할 수 없으면 예측 불가다. 대신할 숫자를 만들지 않는다.**

| 상황 | 관리자가 받는 것 |
|---|---|
| FastAPI 다운 / 연결 거부 | 503 `AI_SERVICE_UNAVAILABLE`, `data: null` |
| 응답 시간 초과 | 503 |
| FastAPI 503 (artifact 없음, 이력 없음, 운영에서 합성 모델 거부) | 503 |
| FastAPI 401 (서비스 토큰 불일치) | 503 |
| FastAPI 422 / 500 | 503 |
| 응답이 계약을 벗어남 (JSON 아님, 날짜 불일치, 음수·누락 식수, model.version·syntheticData·disclaimer·reference 누락) | 503 |
| AI 주소·토큰 미설정 | 503 (기동은 된다) |

- 응답 본문에는 `ErrorCode`의 사용자 문구만 나간다. 내부 URL, 토큰, 스택 트레이스,
  FastAPI의 `detail`은 싣지 않는다.
- 원인 구분(HTTP 401, ConnectException, SocketTimeoutException 등)은 서버 로그에만 남는다.
- 다시 보내지 않는다(재시도 없음).
- FastAPI가 모르는 필드를 응답에 더해도 읽기에 실패하지 않는다.

## 설정

기존 AI 설정을 그대로 쓴다. 새 키를 만들지 않았다.

| Spring 환경 변수 | 설정 키 | 예시 |
|---|---|---|
| `AI_SERVICE_BASE_URL` | `bapplanner.ai.base-url` | 로컬 `http://127.0.0.1:8001`, 컨테이너 `http://ai-server:8000` |
| `AI_SERVICE_TOKEN` | `bapplanner.ai.service-token` | FastAPI의 `AI_SERVICE_TOKEN`과 **같은 값** |

**시간 제한.** `bapplanner.ai.connect-timeout: 5s`,
`read-timeout: 30s`를 그대로 따른다. 한 기능만 다른 시간 제한을 가지면 알아차리기
어렵다는 기존 방침 때문이다. 식수 추론은 실측 수십 ms라, 필요해지면 식수 예측 전용으로
더 짧은 read timeout을 따로 두는 것을 검토한다.

**로그.** 예측일, 성공/실패, 소요 시간(ms), 모델 버전, HTTP 상태만 남긴다. 서비스
토큰, 요청 본문 전체는 남기지 않는다.

```
식수 예측 성공. date=2026-09-15 modelVersion=54453980807e synthetic=true elapsedMs=21
식수 예측 호출 실패. date=2026-09-15 status=HTTP 401 elapsedMs=8
```

## 로컬 실행

두 서비스의 `AI_SERVICE_TOKEN`이 **같아야 한다**(32자 이상).

FastAPI (babplanner_ai):

    APP_ENV=local
    AI_SERVICE_TOKEN=<same-token>
    uvicorn babplanner_ai.main:app --host 127.0.0.1 --port 8001

Spring (babplanner_backend), `docs/mvp_demo_runbook.md`의 DB·키 설정에 더해:

    SPRING_PROFILES_ACTIVE=local
    AI_SERVICE_BASE_URL=http://127.0.0.1:8001
    AI_SERVICE_TOKEN=<same-token>

확인:

    curl -X POST http://127.0.0.1:8080/api/v1/admin/demand-forecasts/predict \
      -H "Authorization: Bearer <관리자 access token>" \
      -H "Content-Type: application/json" \
      -d @forecast-request.json

실제 FastAPI를 향한 자동 확인(각각 해당 환경 변수가 있을 때만 돈다):

    # 정상: Spring을 거친 값이 FastAPI 직접 호출과 같은지
    AI_SERVICE_LIVE_BASE_URL=http://127.0.0.1:8001 AI_SERVICE_TOKEN=<same-token> \
      ./gradlew test --tests "*LiveDemandForecastE2ETest"

    # 실패: 틀린 토큰 또는 죽은 주소 → 숫자 없는 503
    AI_FAILURE_BASE_URL=http://127.0.0.1:1 AI_FAILURE_TOKEN=<anything> \
      ./gradlew test --tests "*LiveDemandForecastFailureE2ETest"

## 현재 입력 기반인 것 (TODO)

지금은 예측 조건을 관리자가 전부 입력한다. 최종 제품에서는 대부분 백엔드가 채운다.

| 입력 | 현재 | 향후 |
|---|---|---|
| `temperature`, `rainfall`, `humidity` | 예측 시점에 알 수 있는 D-1 기상 **예보값**(예보 기온·강수량·습도)을 관리자가 수동 입력. 기상 API 연동은 없음 | 현재 범위에서는 변경 계획 없음 |
| `semester`, `semesterWeek`, `examPeriod`, `holiday`, `campusEvent` | 입력값 | Spring DB의 학사일정으로 자동 계산 |
| `menuCategory`, `mainMenu` | 입력값 | 등록된 식단(Menu)에서 가져오기 |
| 결과 저장 | 안 함 | 저장한다면 modelVersion·syntheticData·forecastDate·predictedMealCount·createdAt 함께 |

## 입력 의미와 한계

- `temperature`, `rainfall`, `humidity`는 예측 시점에 알 수 있는 D-1 기상 예보값(예보 기온·예보 강수량·예보 습도)이다. 관리자가 수동으로 입력하며, 기상 API 연동은 없다.
- 메뉴 인기도(`menuPopularity`)는 계약에서 제거했다. 현재 얻을 수 없는 시뮬레이션 가정이었기 때문이다. 실제 운영 데이터가 쌓이면 같은 메뉴의 과거 평균 식수처럼 계산 가능한 feature로 대체할 수 있다. 지금 새 feature를 추가하지는 않는다.
- CCTV 관련 feature는 전 운영일 또는 전주 같은 요일의 값이다. CCTV가 성능을 개선한다고 주장하지 않는다. CCTV를 포함하는 구조가 합성 환경에서 검증됐을 뿐이며, 실제 기여도는 운영 데이터로 평가해야 한다.

## 코드 위치

| 파일 | 역할 |
|---|---|
| `admin/controller/AdminDemandForecastController` | 관리자 API |
| `admin/service/AdminDemandForecastService` | 요청 → AI 계약 변환, AI 실패 → 503 |
| `admin/dto/DemandForecastRequest` / `DemandForecastResponse` / `DemandForecastStatusResponse` | 관리자 API 계약 |
| `demand/client/DemandForecastAiClient` | FastAPI 호출, 토큰 헤더, 응답 계약 확인 |
| `demand/client/DemandForecastAiRequest` / `DemandForecastAiResponse` / `DemandForecastAiStatus` | FastAPI 내부 계약 |
| `common/client/AiRestClientProvider` (기존) | 주소·토큰·시간 제한 공유 |
