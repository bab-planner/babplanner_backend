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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입용 휴대폰 인증.
 *
 * <p>인증번호와 verificationToken 모두 원문을 저장하지 않는다. 인증번호는
 * entropy가 낮아 키가 들어간 HMAC({@link VerificationCodeHasher})으로,
 * verificationToken은 entropy가 충분해 SHA-256으로 해시한다. 인증번호는
 * 응답에 절대 포함하지 않으며 SmsSender로만 나간다.
 */
@Service
public class PhoneVerificationService {

    private final PhoneVerificationRepository repository;
    private final SmsSender smsSender;
    private final VerificationCodeHasher codeHasher;
    private final AuthProperties properties;
    private final Clock clock;
    private final String smsMode;

    public PhoneVerificationService(
            PhoneVerificationRepository repository,
            SmsSender smsSender,
            VerificationCodeHasher codeHasher,
            AuthProperties properties,
            Clock clock,
            @Value("${bapplanner.sms.mode:}") String smsMode) {
        this.smsMode = smsMode;
        this.repository = repository;
        this.smsSender = smsSender;
        this.codeHasher = codeHasher;
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
                new PhoneVerification(
                        phone, codeHasher.hash(phone, code), expiresAt, resendAvailableAt, now));

        // 코드 원문은 여기서만 흘러가고 어디에도 남지 않는다.
        smsSender.sendVerificationCode(phone, code);

        return new PhoneSendCodeResponse(
                (int) properties.getVerificationCodeTtl().toSeconds(),
                (int) properties.getVerificationResendCooldown().toSeconds(),
                // noop(개발 전용)일 때만 코드를 돌려준다. 로그에는 남기지 않는다.
                "noop".equalsIgnoreCase(smsMode) ? code : null);
    }

    /**
     * 인증번호 확인.
     *
     * <p>오입력 횟수를 올린 결과가 롤백되면 무차별 대입을 막을 수 없으므로 ApiException에는
     * 롤백하지 않는다. 시도 슬롯은 조건부 UPDATE로 원자적으로 확보한다.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PhoneVerifyResponse verify(String phone, String code) {
        Instant now = clock.instant();
        PhoneVerification verification = repository
                .findFirstByPhoneOrderByIdDesc(phone)
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_VERIFICATION_CODE));

        if (verification.isCodeExpired(now)) {
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        // 시도 슬롯 확보. 한도에 이르면 코드가 맞아도 실패한다.
        if (repository.reserveAttempt(verification.getId(), properties.getVerificationMaxAttempts())
                != 1) {
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        if (!codeHasher.matches(phone, code, verification.getCodeHash())) {
            // 만료와 불일치를 같은 오류로 응답해 어떤 쪽인지 추측하기 어렵게 한다.
            throw new ApiException(ErrorCode.INVALID_VERIFICATION_CODE);
        }
        // 성공한 시도는 실패 횟수에 세지 않는다.
        repository.releaseAttempt(verification.getId());

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
