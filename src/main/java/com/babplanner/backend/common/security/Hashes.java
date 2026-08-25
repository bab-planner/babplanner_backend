package com.babplanner.backend.common.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * 토큰/인증번호용 난수 생성과 단방향 해시.
 *
 * <p>refresh token, verificationToken, 인증번호는 원문을 저장하지 않고
 * 여기서 만든 SHA-256 해시만 DB에 넣는다. 비밀번호는 여기가 아니라
 * BCryptPasswordEncoder를 쓴다(느린 해시가 필요하므로).
 */
public final class Hashes {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

    private Hashes() {}

    /** URL-safe 난수 토큰. 32바이트면 추측 공격에 충분하다. */
    public static String randomToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return URL_ENCODER.encodeToString(bytes);
    }

    /** 6자리 숫자 인증번호. 앞자리가 0이어도 6자리를 유지한다. */
    public static String randomNumericCode() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }
}
