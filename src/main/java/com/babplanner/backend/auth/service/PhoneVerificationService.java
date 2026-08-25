package com.babplanner.backend.auth.service;

import com.babplanner.backend.auth.domain.PhoneVerification;
import com.babplanner.backend.auth.dto.PhoneSendCodeResponse;
import com.babplanner.backend.auth.dto.PhoneVerifyResponse;
import com.babplanner.backend.auth.repository.PhoneVerificationRepository;
import com.babplanner.backend.common.config.AuthProperties;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.common.security.Hashes;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입용 휴대폰 인증.
 *
 * <p>인증번호와 verificationToken 모두 원문을 저장하지 않고 SHA-256 해시만
 * 남긴다. 인증번호는 응답에 절대 포함하지 않으며 SmsSender로만 나간다.
 */
@Service
public class PhoneVerificationService {

    private final PhoneVerificationRepository repository;
    private final SmsSender smsSender;
    private final AuthProperties properties;
    private final Clock clock;

    public PhoneVerificationService(
            PhoneVerificationRepository repository,
            SmsSender smsSender,
            AuthProperties properties,
            Clock clock) {
        this.repository = repository;
        this.smsSender = smsSender;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public PhoneSendCodeResponse sendCode(String phone) {
        Instant now = clock.instant();

        // 직전 요청이 아직 재전송 대기 중이면 새 코드를 만들지 않는다.
        Optional<PhoneVerification> latest = repository.findFirstByPhoneOrderByIdDesc(phone);
        if (latest.isPresent() && now.isBefore(latest.get().getResendAvailableAt())) {
            throw new ApiException(ErrorCode.RESEND_TOO_SOON);
        }

        String code = Hashes.randomNumericCode();
        Instant expiresAt = now.plus(properties.getVerificationCodeTtl());
        Instant resendAvailableAt = now.plus(properties.getVerificationResendCooldown());

        repository.save(
                new PhoneVerification(phone, Hashes.sha256(code), expiresAt, resendAvailableAt, now));

        // 코드 원문은 여기서만 흘러가고 어디에도 남지 않는다.
        smsSender.sendVerificationCode(phone, code);

        return new PhoneSendCodeResponse(
                (int) properties.getVerificationCodeTtl().toSeconds(),
                (int) properties.getVerificationResendCooldown().toSeconds());
    }

    @Transactional
    public PhoneVerifyResponse verify(String phone, String code) {
        Instant now = clock.instant();
        PhoneVerification verification = repository
                .findFirstByPhoneOrderByIdDesc(phone)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_CODE));

        if (verification.isCodeExpired(now)) {
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        if (!verification.getCodeHash().equals(Hashes.sha256(code))) {
            // 만료와 불일치를 같은 오류로 응답해 어떤 쪽인지 추측하기 어렵게 한다.
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }

        String verificationToken = Hashes.randomToken();
        Duration tokenTtl = properties.getVerificationTokenTtl();
        verification.markVerified(Hashes.sha256(verificationToken), now.plus(tokenTtl), now);

        return new PhoneVerifyResponse(verificationToken, (int) tokenTtl.toSeconds());
    }

    /**
     * 회원가입 시 verificationToken을 소비한다.
     *
     * <p>토큰이 유효하고, 만료되지 않았고, 아직 쓰이지 않았고, 인증된 번호가
     * 가입 요청의 번호와 같아야 한다. 성공하면 즉시 사용 처리해 재사용을 막는다.
     */
    @Transactional
    public void consumeVerificationToken(String verificationToken, String phone) {
        Instant now = clock.instant();
        PhoneVerification verification = repository
                .findByVerificationTokenHash(Hashes.sha256(verificationToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_TOKEN));

        if (verification.getVerifiedAt() == null
                || verification.isTokenUsed()
                || verification.isTokenExpired(now)
                || !verification.getPhone().equals(phone)) {
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_TOKEN);
        }

        verification.markTokenUsed(now);
    }
}
