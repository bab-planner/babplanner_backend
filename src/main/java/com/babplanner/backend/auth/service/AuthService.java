package com.babplanner.backend.auth.service;

import com.babplanner.backend.auth.domain.RefreshSession;
import com.babplanner.backend.auth.dto.AuthResponse;
import com.babplanner.backend.auth.dto.LoginRequest;
import com.babplanner.backend.auth.dto.SignupRequest;
import com.babplanner.backend.auth.repository.RefreshSessionRepository;
import com.babplanner.backend.common.config.AuthProperties;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.common.security.Hashes;
import com.babplanner.backend.common.security.JwtTokenService;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import com.babplanner.backend.user.dto.UserResponse;
import com.babplanner.backend.user.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 / 로그인 / 토큰 재발급 / 로그아웃.
 *
 * <p>refresh token은 JWT가 아니라 SecureRandom opaque 문자열이며, DB에는
 * SHA-256 해시만 저장한다. 재발급 시 기존 세션을 원자적으로 revoke하고 새
 * 세션을 만들어(rotation) 사용된 토큰의 재사용을 막는다.
 */
@Service
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshSessionRepository refreshSessionRepository;
    private final PhoneVerificationService phoneVerificationService;
    private final JwtTokenService jwtTokenService;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties properties;
    private final Clock clock;

    public AuthService(
            UserRepository userRepository,
            RefreshSessionRepository refreshSessionRepository,
            PhoneVerificationService phoneVerificationService,
            JwtTokenService jwtTokenService,
            PasswordEncoder passwordEncoder,
            AuthProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.refreshSessionRepository = refreshSessionRepository;
        this.phoneVerificationService = phoneVerificationService;
        this.jwtTokenService = jwtTokenService;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public AuthResponse signup(SignupRequest request) {
        if (!Boolean.TRUE.equals(request.agreements().serviceTerms())
                || !Boolean.TRUE.equals(request.agreements().privacyPolicy())) {
            throw new ApiException(ErrorCode.TERMS_NOT_AGREED);
        }

        // 토큰 소비가 먼저다. 유효하지 않으면 계정을 만들지 않는다.
        phoneVerificationService.consumeVerificationToken(request.verificationToken(), request.phone());

        if (userRepository.existsByPhone(request.phone())) {
            throw new ApiException(ErrorCode.DUPLICATE_PHONE);
        }
        if (userRepository.existsByStudentId(request.studentId())) {
            throw new ApiException(ErrorCode.DUPLICATE_STUDENT_ID);
        }

        Instant now = clock.instant();
        UserEntity user = userRepository.save(new UserEntity(
                request.name(),
                request.phone(),
                request.studentId(),
                request.gender(),
                Role.USER,
                passwordEncoder.encode(request.password()),
                now));

        return issueTokens(user, now);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 번호가 없을 때와 비밀번호가 틀렸을 때를 같은 오류로 응답한다.
        UserEntity user = userRepository
                .findByPhone(request.phone())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_CREDENTIALS));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new ApiException(ErrorCode.INVALID_CREDENTIALS);
        }

        return issueTokens(user, clock.instant());
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        Instant now = clock.instant();
        RefreshSession session = refreshSessionRepository
                .findByTokenHash(Hashes.sha256(refreshToken))
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (session.isExpired(now)) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 살아 있는 세션만 revoke된다. 동시에 같은 토큰이 들어오면 한쪽만 1을 받는다.
        if (refreshSessionRepository.revokeIfActive(session.getId(), now) != 1) {
            throw new ApiException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        UserEntity user = userRepository
                .findById(session.getUserId())
                .orElseThrow(() -> new ApiException(ErrorCode.INVALID_REFRESH_TOKEN));

        return issueTokens(user, now);
    }

    /**
     * 로그아웃.
     *
     * <p>refreshToken이 오면 해당 세션만 revoke한다. 없거나 이미 revoke된
     * 토큰이어도 오류로 만들지 않는다. 클라이언트의 로컬 로그아웃을 막지 않기
     * 위해서다. access token blacklist는 두지 않고 짧은 TTL로 자연 만료시킨다.
     */
    @Transactional
    public void logout(Long userId, String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        refreshSessionRepository
                .findByTokenHash(Hashes.sha256(refreshToken))
                .filter(session -> session.getUserId().equals(userId))
                .ifPresent(session -> refreshSessionRepository.revokeIfActive(session.getId(), now));
    }

    private AuthResponse issueTokens(UserEntity user, Instant now) {
        String accessToken = jwtTokenService.issueAccessToken(user);
        String refreshToken = Hashes.randomToken();
        refreshSessionRepository.save(new RefreshSession(
                user.getId(),
                Hashes.sha256(refreshToken),
                now.plus(properties.getRefreshTokenTtl()),
                now));
        return new AuthResponse(accessToken, refreshToken, UserResponse.from(user));
    }
}
