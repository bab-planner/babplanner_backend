package com.babplanner.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 기반 인증번호 해시.
 *
 * <p>입력에 휴대폰 번호를 함께 넣어 같은 코드라도 번호가 다르면 다른 해시가
 * 나오게 한다. 비교는 {@link MessageDigest#isEqual}로 상수 시간에 한다.
 *
 * <p>키는 JWT 서명 키와 반드시 달라야 한다. 하나가 유출됐을 때 다른 하나까지
 * 무력화되는 것을 막기 위해서다.
 */
public class HmacVerificationCodeHasher implements VerificationCodeHasher {

    private static final String ALGORITHM = "HmacSHA256";
    private static final int MIN_KEY_LENGTH = 32;

    private final SecretKeySpec key;

    public HmacVerificationCodeHasher(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "bapplanner.auth.phone-verification-hmac-key가 설정되지 않았습니다. "
                            + "PHONE_VERIFICATION_HMAC_KEY 환경 변수로 주입하세요.");
        }
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < MIN_KEY_LENGTH) {
            throw new IllegalStateException(
                    "bapplanner.auth.phone-verification-hmac-key는 최소 " + MIN_KEY_LENGTH + "바이트여야 합니다.");
        }
        this.key = new SecretKeySpec(bytes, ALGORITHM);
    }

    @Override
    public String hash(String phone, String code) {
        try {
            Mac mac = Mac.getInstance(ALGORITHM);
            mac.init(key);
            byte[] result = mac.doFinal((phone + ":" + code).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(result);
        } catch (NoSuchAlgorithmException | java.security.InvalidKeyException exception) {
            throw new IllegalStateException("인증번호 해시를 계산할 수 없습니다.", exception);
        }
    }

    @Override
    public boolean matches(String phone, String code, String hash) {
        if (hash == null) {
            return false;
        }
        // 타이밍 차이로 해시를 추측할 수 없도록 상수 시간 비교를 쓴다.
        return MessageDigest.isEqual(
                hash(phone, code).getBytes(StandardCharsets.UTF_8),
                hash.getBytes(StandardCharsets.UTF_8));
    }
}
