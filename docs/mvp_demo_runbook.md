# 밥플래너 MVP 로컬 데모 실행

네 프로세스를 순서대로 띄운다. 각 단계의 값은 `docs/mvp_environment.md` 를
본다. 여기에는 실제 비밀 값을 적지 않는다. `<your-secret>` 자리에 직접
넣는다.

```
1. MySQL  →  2. FastAPI  →  3. Spring  →  4. Admin Web  →  5. Flutter App
                                 ↑
                          6. CCTV Collector (선택)
```

앞 단계가 떠 있어야 다음 단계가 동작한다.

---

## 1. MySQL 8.4

```
cd babplanner_backend
MYSQL_ROOT_PASSWORD=<your-secret> \
MYSQL_USER=<your-user> \
MYSQL_PASSWORD=<your-secret> \
MYSQL_PORT=3307 \
docker compose up -d
```

호스트에 이미 MySQL이 있을 수 있어 3307로 매핑한다. 스키마 생성은 Spring이
기동할 때 Flyway가 한다.

---

## 2. FastAPI (AI)

```
cd babplanner_ai
APP_ENV=local
AI_SERVICE_TOKEN=<your-secret-32chars-or-more>

.venv/Scripts/python.exe -m uvicorn babplanner_ai.main:app --host 127.0.0.1 --port 8001
```

> AI 서비스는 **다음 영업일 점심 식수 예측**(`/internal/v1/ai/demand-forecast/*`)만
> 제공한다. 현재 모델은 합성데이터로 학습한 PoC이며 응답에 그 사실이 표시된다
> (docs/demand_forecast_poc.md). 혼잡도는 AI 서비스를 거치지 않고 CCTV 수집기가
> Spring에 직접 보낸다.

---

## 3. Spring Backend

```
cd babplanner_backend
./gradlew.bat bootJar

DB_URL='jdbc:mysql://127.0.0.1:3307/babplanner?serverTimezone=UTC&characterEncoding=UTF-8'
DB_USERNAME=<your-user>
DB_PASSWORD=<your-secret>
JWT_SIGNING_KEY=<your-secret-32bytes-or-more>
PHONE_VERIFICATION_HMAC_KEY=<another-secret-32bytes-or-more>
BAPPLANNER_BUSINESS_ZONE_ID=Asia/Seoul
BAPPLANNER_INTERNAL_SERVICE_TOKEN=<your-secret-32chars-or-more>
AI_SERVICE_BASE_URL=http://127.0.0.1:8001
AI_SERVICE_TOKEN=<2단계와 같은 값>
SPRING_PROFILES_ACTIVE=local
SMS_MODE=noop
WASTE_STORAGE_MODE=local
WASTE_LOCAL_ROOT=<local-directory>
DEV_ADMIN_ENABLED=true
DEV_ADMIN_PHONE=<demo-phone>
DEV_ADMIN_PASSWORD=<your-secret>
DEV_ADMIN_STUDENT_ID=<demo-id>
DEV_ADMIN_NAME=<demo-name>

java -jar build/libs/babplanner-backend-0.0.1-SNAPSHOT.jar
```

기동하면 Flyway가 V1~V29를 적용한다. 성공하면 로그에 `Started
BabplannerBackendApplication` 이 찍힌다.

잔반 인증은 AI가 아니라 관리자가 사진을 보고 직접 승인/반려한다(docs/waste_review.md).

### 관리자 계정

`DevAdminSeeder`는 `local`/`dev` 프로필에서 `DEV_ADMIN_ENABLED=true` 일 때만
동작한다. 이미 같은 번호의 관리자가 있으면 **비밀번호를 덮어쓰지 않는다.**
같은 번호의 일반 사용자가 있으면 기동에 실패한다(설정 한 줄로 권한이 올라가지
않게 하려는 것이다).

데모 비밀번호는 환경변수로만 넣는다. Git에 넣지 않는다.

---

## 4. Admin Web

```
cd babplanner_admin
npm ci
npm run dev
```

`http://localhost:5173` 으로 열린다. 개발 서버가 `/api` 요청을 8080으로
중계하므로 백엔드에 CORS 설정이 필요 없다.

다른 포트/주소를 쓰려면 `VITE_DEV_API_TARGET` 을 바꾼다.

---

## 5. Flutter 사용자 앱

```
cd babplanner_app
flutter run --dart-define=APP_DATA_MODE=real --dart-define=API_BASE_URL=http://10.0.2.2:8080
```

`10.0.2.2` 는 Android 에뮬레이터에서 호스트를 가리키는 주소다. 실제 기기라면
같은 네트워크의 PC IP를 넣는다.

`APP_DATA_MODE` 를 빼면 앱은 Fake 데이터로 동작하고 서버를 부르지 않는다.

---

## 6. CCTV 수집기 (선택)

녹화 영상에서 혼잡도 관측을 만들어 Spring으로 보낸다. 자세한 것은
`babplanner_ai/demo/cctv_collector/README.md`.

**기본이 dry-run이다.** `--send` 를 붙여야 서버에 쓴다.

```
cd babplanner_ai

:: 1) 아무것도 보내지 않고 화면으로만 확인
.venv-cctv\Scripts\python.exe -m cctv_collector ^
  --video <VIDEO_PATH> ^
  --config demo\cctv_collector\cameras\demo_camera.example.yaml ^
  --limit-seconds 30

:: 2) 실제 전송. 주소와 토큰은 환경 변수로만 준다
set BAPPLANNER_BACKEND_BASE_URL=<BACKEND_URL>
set BAPPLANNER_INTERNAL_SERVICE_TOKEN=<your-secret>
.venv-cctv\Scripts\python.exe -m cctv_collector ^
  --video <VIDEO_PATH> ^
  --config demo\cctv_collector\cameras\demo_camera.example.yaml ^
  --site-id <SITE_ID> --send --limit-seconds 30
```

`BAPPLANNER_INTERNAL_SERVICE_TOKEN` 은 Spring이 **받는** 토큰이다. Spring이
FastAPI로 **보내는** `AI_SERVICE_TOKEN` 과 다른 값이다.

`<SITE_ID>` 를 추측하지 않는다. 관리자 웹 상단 급식소 선택기나
`GET /api/v1/congestion/current?siteId=N` 으로 확인한 값을 쓴다.

**카메라를 처음 다는 경우** ROI와 counting line을 먼저 눈으로 확인한다.

```
.venv-cctv\Scripts\python.exe -m cctv_collector ^
  --video <VIDEO_PATH> --config <CAMERA_YAML> --preview-frame 100
```

> **혼잡 단계 임계값은 시연용이다.** 저장소의 예시는
> `DEMO_ONLY_UNCALIBRATED` 로 표시되어 있고, 실제 급식소에서 보정한 값이
> 아니다. 시연에서 "실제 호서대학교 기준"처럼 소개하지 않는다.

---

## 5분 데모 흐름

### 사용자 앱

1. **로그인** — 개발용 사용자 계정(`DEV_USER_*`)으로 들어간다.
2. **메뉴** — 날짜·식사 구분으로 식단과 알레르기 정보를 본다.
3. **혼잡도** — 현재 혼잡도를 본다.
4. **잔반 인증** — 사진을 올리면 PENDING으로 저장된다. 관리자가 승인하면
   포인트가 적립된다(docs/waste_review.md).

### CCTV 혼잡도 (수집기를 띄운 경우)

- 관리자 웹 대시보드의 **현재 혼잡도** 카드는 5초마다 스스로 다시 읽는다.
  수집기를 `--send` 로 돌리면 화면을 건드리지 않아도 인원과 단계가 바뀐다.
- 사용자 앱 홈은 **아래로 당기면** 다시 읽는다. 배터리를 쓰는 폴링을 넣지
  않았다.
- 저장된 출처는 `CCTV_AI` 다. 수집기가 보내는 값이 아니라 서버가 기록한다.

말할 때 주의:

- "AI가 혼잡도를 판정한다" (X) → 인원수는 검출 결과이고, **단계는 운영
  임계값**이 정한다.
- "식수를 센다" (X) → 입장 인원은 검증되지 않았고 서버에 저장하지도 않는다.
- "급식소에서 검증했다" (X) → 공개 감시 데이터(CAVIAR)로 평가했다.

### 관리자 웹

5. **대시보드** — 현재 혼잡도와 오늘 잔반 요약을 본다.
6. **잔반 검토** — 대기 중인 제출의 사진을 보고 승인/반려한다.
7. **식수 예측** — 다음 영업일 점심 식수를 예측한다(합성데이터 PoC 모델).

데모 중에 식수 숫자를 소개할 때는 합성데이터로 학습한 PoC라는 사실을 함께 말한다.

---

## 데모 데이터 초기화

반복 시연할 때 쓴다. **운영 데이터에는 절대 쓰지 않는다.**

로컬 Docker 볼륨을 지우는 방식만 문서화한다. 테이블을 전부 비우는 스크립트는
두지 않는다. 그런 스크립트는 어느 환경에서 실행됐는지 나중에 알 수 없다.

```
cd babplanner_backend
docker compose down -v      # 로컬 볼륨까지 삭제
docker compose up -d        # 빈 DB로 다시 시작
```

다음 Spring 기동 때 Flyway가 V1부터 다시 적용하고 씨앗 데이터가 들어간다.
잔반 이미지는 `WASTE_LOCAL_ROOT` 디렉터리에 남으므로 필요하면 따로 지운다.

---

## 자주 겪는 문제

| 증상 | 원인 |
|---|---|
| 기동 실패: `bapplanner.business-zone-id를 설정해야 합니다` | `BAPPLANNER_BUSINESS_ZONE_ID` 누락. 의도된 fail-fast다 |
| 기동 실패: `bapplanner.internal.service-token 이 필요합니다` | 내부 토큰 누락 |
| 식수 예측 실행이 503 | FastAPI가 꺼져 있거나 모델 artifact를 찾지 못했다 |
| 관리자 로그인 실패 | 이미 있는 관리자의 비밀번호는 seeder가 덮지 않는다. 처음 만들 때 쓴 값을 쓴다 |
| 앱이 서버를 안 부름 | `APP_DATA_MODE=real` 과 `API_BASE_URL` 을 둘 다 넣었는지 확인 |
| 수집기가 전송을 거부 | 카메라 설정에 `congestion_policy` 가 없다. dry-run은 되지만 `--send` 는 임계값을 요구한다 |
| 수집기 POST가 401 | `BAPPLANNER_INTERNAL_SERVICE_TOKEN` 이 Spring 쪽 값과 다르다 |
| 202는 오는데 화면이 안 바뀜 | 보낸 `observedAt` 이 저장된 현재값보다 과거다. 수집기는 관측 시각을 실제 시각으로 보내므로 정상 실행에서는 생기지 않는다 |
| 앱 홈이 예전 값 | 아래로 당겨 새로 고친다. 앱은 폴링하지 않는다 |
