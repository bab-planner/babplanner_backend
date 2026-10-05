# 회원 탈퇴, 개발용 인증번호, 인증 시도 제한

## 회원 탈퇴 `POST /api/v1/users/me/deletion`

- 인증 필요, `ROLE_USER`만 가능하다. 관리자는 403 `FORBIDDEN`.
- 요청: `{"password": "현재 비밀번호"}`
- 성공: 204, 본문 없음.
- 비밀번호 불일치: **400 `INVALID_PASSWORD`**. 앱이 401을 세션 만료로 해석해 로그아웃시키므로 401을 쓰지 않는다.
- 이미 탈퇴한 사용자의 토큰: 401 (인증 필터가 사용자 존재를 확인한다). 두 번째 호출도 401.

### 삭제 범위

한 트랜잭션에서 FK 순서대로 명시적으로 지운다(ON DELETE CASCADE 없음).

`survey_answer_options` → `survey_answers` → `survey_responses` → `waste_submissions` → `inquiries` → `coupons` → `reward_transactions` → `reward_accounts` → `user_allergies` → `refresh_sessions` → `phone_verifications`(해당 사용자 번호) → `users`

- 다른 사용자의 데이터는 건드리지 않는다.
- 잔반 이미지 파일은 DB 커밋 이후 best-effort로 삭제한다. 파일 삭제 실패는 탈퇴를 실패시키지 않으며, 로그에는 실패 건수만 남긴다.
- 탈퇴 후 같은 휴대폰 번호와 학번으로 다시 가입할 수 있다.

## 개발용 인증번호 `developmentCode`

- `SMS_MODE=noop`일 때만 `POST /api/v1/auth/phone/send-code` 응답 `data.developmentCode`에 6자리 코드가 포함된다.
- 그 외 모드에서는 키 자체가 응답에 없다.
- **`SMS_MODE=noop`은 공개 환경(운영, 외부에서 접근 가능한 스테이징)에서 절대 사용하면 안 된다.** 누구나 인증번호를 받아 휴대폰 인증을 우회할 수 있다.
- 서버 저장은 지금처럼 HMAC 해시만 하며 코드는 로그에 남기지 않는다.

## 인증번호 무차별 대입 제한

- `phone_verifications.failed_attempts`(V27)에 오입력 횟수를 원자적 조건부 UPDATE로 올린다.
- 한 인증 요청에서 허용 시도는 `bapplanner.auth.verification-max-attempts`(기본 5, 1 이상). 한도에 이르면 이후 verify는 코드가 맞아도 `INVALID_VERIFICATION_CODE`다. 새 인증번호를 다시 요청해야 한다.

## 기타 입력 제한

- 업로드 크기 초과(`MaxUploadSizeExceededException`)는 422 `INVALID_WASTE_IMAGE` 오류 봉투로 응답한다.
- 문의 `title` 최대 200자, `content` 최대 5000자. 초과 시 400 `VALIDATION_ERROR`.
