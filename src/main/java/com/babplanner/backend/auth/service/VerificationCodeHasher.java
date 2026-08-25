package com.babplanner.backend.auth.service;

/**
 * SMS 인증번호 해시 경계.
 *
 * <p>인증번호는 6자리라 경우의 수가 100만뿐이다. 평문 SHA-256만 쓰면 DB가
 * 유출됐을 때 전수 계산으로 원문을 즉시 복원할 수 있으므로, 서버만 아는
 * 비밀 키가 들어간 HMAC을 사용한다. refresh token이나 verificationToken처럼
 * entropy가 충분한 값과는 다른 취급이 필요해 별도 인터페이스로 분리한다.
 */
public interface VerificationCodeHasher {

    String hash(String phone, String code);

    boolean matches(String phone, String code, String hash);
}
