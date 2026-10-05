package com.babplanner.backend.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 휴대폰 인증 challenge.
 *
 * <p>인증번호와 회원가입용 verificationToken 모두 해시만 저장한다. 원문은
 * 발급 시점에 한 번만 전달하고(코드는 SMS, 토큰은 응답) 서버에 남기지 않는다.
 */
@Entity
@Table(name = "phone_verifications")
public class PhoneVerification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String phone;

    @Column(name = "code_hash", nullable = false, length = 64)
    private String codeHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "resend_available_at", nullable = false)
    private Instant resendAvailableAt;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "verification_token_hash", length = 64)
    private String verificationTokenHash;

    @Column(name = "verification_token_expires_at")
    private Instant verificationTokenExpiresAt;

    @Column(name = "token_used_at")
    private Instant tokenUsedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 오입력 횟수. 조건부 UPDATE로만 바꾸므로 엔티티 저장 시 덮어쓰지 않는다. */
    @Column(name = "failed_attempts", insertable = false, updatable = false)
    private int failedAttempts;

    protected PhoneVerification() {
        // JPA 전용
    }

    public PhoneVerification(
            String phone,
            String codeHash,
            Instant expiresAt,
            Instant resendAvailableAt,
            Instant now) {
        this.phone = phone;
        this.codeHash = codeHash;
        this.expiresAt = expiresAt;
        this.resendAvailableAt = resendAvailableAt;
        this.createdAt = now;
    }

    /** 인증 성공. 회원가입에 쓸 단기 1회성 토큰의 해시를 함께 보관한다. */
    public void markVerified(String tokenHash, Instant tokenExpiresAt, Instant now) {
        this.verifiedAt = now;
        this.verificationTokenHash = tokenHash;
        this.verificationTokenExpiresAt = tokenExpiresAt;
        this.tokenUsedAt = null;
    }

    /** 회원가입에 사용됨. 같은 토큰으로 두 번째 가입을 막는다. */
    public void markTokenUsed(Instant now) {
        this.tokenUsedAt = now;
    }

    public boolean isCodeExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    public boolean isTokenExpired(Instant now) {
        return verificationTokenExpiresAt == null || !now.isBefore(verificationTokenExpiresAt);
    }

    public boolean isTokenUsed() {
        return tokenUsedAt != null;
    }

    public int getFailedAttempts() {
        return failedAttempts;
    }

    public Long getId() {
        return id;
    }

    public String getPhone() {
        return phone;
    }

    public String getCodeHash() {
        return codeHash;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getResendAvailableAt() {
        return resendAvailableAt;
    }

    public Instant getVerifiedAt() {
        return verifiedAt;
    }

    public String getVerificationTokenHash() {
        return verificationTokenHash;
    }

    public Instant getVerificationTokenExpiresAt() {
        return verificationTokenExpiresAt;
    }

    public Instant getTokenUsedAt() {
        return tokenUsedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
