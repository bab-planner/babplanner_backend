# 밥플래너 MVP 로컬 데모 실행

네 프로세스를 순서대로 띄운다. 각 단계의 값은 `docs/mvp_environment.md` 를
본다. 여기에는 실제 비밀 값을 적지 않는다. `<your-secret>` 자리에 직접
넣는다.

```
1. MySQL      →  2. FastAPI  →  3. Spring  →  4. Admin Web  →  5. Flutter App
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
WASTE_MODEL_MODE=fixture
DEMAND_MODEL_MODE=fixture
PROCUREMENT_MODEL_MODE=fixture
AI_SERVICE_TOKEN=<your-secret-32chars-or-more>

.venv/Scripts/python.exe -m uvicorn babplanner_ai.main:app --host 127.0.0.1 --port 8001
```

> **fixture는 AI가 아니다.**
> 세 기능 모두 실제 모델이 없다. fixture는 입력을 보지 않고 정해진 값을
> 돌려준다. 데모에서 나오는 숫자는 경계가 연결됐다는 증거일 뿐이고, 예측이나
> 추천으로 읽으면 안 된다.
>
> | 기능 | `modelVersion` | 돌려주는 값 |
> |---|---|---|
> | 잔반 분석 | `fastapi-fixture-waste-v1` | 이미지를 판독하지 않은 고정값 |
> | 수요 예측 | `fastapi-fixture-demand-v1` | 항상 100명 |
> | 발주 추천 | `fastapi-fixture-procurement-v1` | 계획 수량 그대로, 조정률 0 |

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
WASTE_ANALYZER_MODE=http
DEV_ADMIN_ENABLED=true
DEV_ADMIN_PHONE=<demo-phone>
DEV_ADMIN_PASSWORD=<your-secret>
DEV_ADMIN_STUDENT_ID=<demo-id>
DEV_ADMIN_NAME=<demo-name>

java -jar build/libs/babplanner-backend-0.0.1-SNAPSHOT.jar
```

기동하면 Flyway가 V1~V25를 적용한다. 성공하면 로그에 `Started
BabplannerBackendApplication` 이 찍힌다.

`WASTE_ANALYZER_MODE=fake` 로 두면 FastAPI 없이도 돌지만, 그 경우 잔반 결과는
백엔드 내부 fixture(`spring-fixture-waste-v1`)가 낸 값이다. 프로세스 경계를
보여 주려면 `http` 로 둔다.

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

## 5분 데모 흐름

### 사용자 앱

1. **로그인** — 개발용 사용자 계정(`DEV_USER_*`)으로 들어간다.
2. **메뉴** — 날짜·식사 구분으로 식단과 알레르기 정보를 본다.
3. **혼잡도** — 현재 혼잡도를 본다.
4. **잔반 인증** — 사진을 올리면 백엔드가 저장하고 FastAPI에 분석을 맡긴다.
   결과가 나올 때까지 화면이 상태를 갱신한다.
   *결과는 fixture가 낸 값이다. 실제 판독이 아니다.*

### 관리자 웹

5. **대시보드** — 현재 혼잡도와 오늘 잔반 요약을 본다.
   예상 식수와 발주 추천 칸은 비어 있다. 버그가 아니라, 대시보드에 식사
   구분이 없어서 어느 끼니를 대표할지 정해진 적이 없기 때문이다.
6. **잔반 분석** — 기간별 평균 잔반률과 메뉴별 잔반률.
7. **예상 식수** — 실행하면 FastAPI를 거쳐 값이 저장된다.
   화면에 **"개발용 fixture — 실제 AI 예측이 아닙니다"** 배지가 함께 뜬다.
8. **발주 추천** — 계획 식재료를 입력하고 실행한다. 저장된 최신 예측을
   근거로 삼으므로 7번을 먼저 해야 한다.
   *fixture는 계획 수량을 그대로 돌려준다. 절감 추천이 아니다.*

데모 중에 숫자를 소개할 때는 fixture라는 사실을 함께 말한다. 그러지 않으면
듣는 사람은 그것을 예측 결과로 받아들인다.

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
| 예측/발주 실행이 503 | FastAPI가 꺼져 있거나 `DEMAND_MODEL_MODE`/`PROCUREMENT_MODEL_MODE` 가 `disabled` |
| 발주 실행이 409 | 같은 식당·날짜·끼니의 예상 식수를 먼저 실행해야 한다 |
| 관리자 로그인 실패 | 이미 있는 관리자의 비밀번호는 seeder가 덮지 않는다. 처음 만들 때 쓴 값을 쓴다 |
| 앱이 서버를 안 부름 | `APP_DATA_MODE=real` 과 `API_BASE_URL` 을 둘 다 넣었는지 확인 |
