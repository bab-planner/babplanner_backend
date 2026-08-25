package com.babplanner.backend.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 관련 설정.
 *
 * <p>TTL을 코드에 흩뿌리지 않고 여기 한 곳에서만 정의한다. signingKey는
 * 환경 변수로 주입하며 기본값을 두지 않는다.
 */
@ConfigurationProperties(prefix = "bapplanner.auth")
public class AuthProperties {

    private Duration accessTokenTtl = Duration.ofMinutes(30);
    private Duration refreshTokenTtl = Duration.ofDays(14);
    private Duration verificationCodeTtl = Duration.ofMinutes(3);
    private Duration verificationResendCooldown = Duration.ofSeconds(60);
    private Duration verificationTokenTtl = Duration.ofMinutes(10);
    private String issuer = "babplanner";

    /**
     * JWT HMAC 서명 키. 최소 32바이트.
     *
     * <p>기본값이 없다. 설정되지 않으면 애플리케이션이 기동에 실패하며,
     * 취약한 기본 키로 조용히 넘어가지 않는다.
     */
    private String signingKey;

    /**
     * 휴대폰 인증번호 HMAC 키. 최소 32바이트.
     *
     * <p>6자리 코드는 entropy가 낮아 평문 해시로는 보호되지 않는다.
     * signingKey와 반드시 다른 값을 써야 한다.
     */
    private String phoneVerificationHmacKey;

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public Duration getVerificationCodeTtl() {
        return verificationCodeTtl;
    }

    public void setVerificationCodeTtl(Duration verificationCodeTtl) {
        this.verificationCodeTtl = verificationCodeTtl;
    }

    public Duration getVerificationResendCooldown() {
        return verificationResendCooldown;
    }

    public void setVerificationResendCooldown(Duration verificationResendCooldown) {
        this.verificationResendCooldown = verificationResendCooldown;
    }

    public Duration getVerificationTokenTtl() {
        return verificationTokenTtl;
    }

    public void setVerificationTokenTtl(Duration verificationTokenTtl) {
        this.verificationTokenTtl = verificationTokenTtl;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getSigningKey() {
        return signingKey;
    }

    public void setSigningKey(String signingKey) {
        this.signingKey = signingKey;
    }

    public String getPhoneVerificationHmacKey() {
        return phoneVerificationHmacKey;
    }

    public void setPhoneVerificationHmacKey(String phoneVerificationHmacKey) {
        this.phoneVerificationHmacKey = phoneVerificationHmacKey;
    }
}
