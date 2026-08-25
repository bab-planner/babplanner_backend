package com.babplanner.backend.common.security;

import com.babplanner.backend.common.config.AuthProperties;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Access Token 발급/검증.
 *
 * <p>Nimbus JOSE(HS256)로 서명한다. claim에는 사용자 식별에 필요한 최소 정보만
 * 넣는다. 휴대폰 번호, 이름, 학번 같은 개인정보는 토큰에 담지 않는다.
 */
@Service
public class JwtTokenService {

    private static final String ROLE_CLAIM = "role";

    private final AuthProperties properties;
    private final Clock clock;
    private final byte[] secret;

    public JwtTokenService(AuthProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
        String signingKey = properties.getSigningKey();
        if (signingKey == null || signingKey.isBlank()) {
            throw new IllegalStateException(
                    "bapplanner.auth.signing-key가 설정되지 않았습니다. "
                            + "환경 변수로 주입하세요. 기본 키로 대체하지 않습니다.");
        }
        this.secret = signingKey.getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException("bapplanner.auth.signing-key는 최소 32바이트여야 합니다.");
        }
    }

    public String issueAccessToken(UserEntity user) {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.getAccessTokenTtl());
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .subject(String.valueOf(user.getId()))
                .claim(ROLE_CLAIM, user.getRole().name())
                .issuer(properties.getIssuer())
                .issueTime(Date.from(now))
                .expirationTime(Date.from(expiresAt))
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
        try {
            jwt.sign(new MACSigner(secret));
        } catch (JOSEException exception) {
            throw new IllegalStateException("Access Token 서명에 실패했습니다.", exception);
        }
        return jwt.serialize();
    }

    /** 서명/만료/발급자가 모두 유효할 때만 principal을 돌려준다. */
    public Optional<AuthenticatedUser> parseAccessToken(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(new MACVerifier(secret))) {
                return Optional.empty();
            }
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            Date expiration = claims.getExpirationTime();
            if (expiration == null || !clock.instant().isBefore(expiration.toInstant())) {
                return Optional.empty();
            }
            if (!properties.getIssuer().equals(claims.getIssuer())) {
                return Optional.empty();
            }
            Long userId = Long.valueOf(claims.getSubject());
            Role role = Role.valueOf(claims.getStringClaim(ROLE_CLAIM));
            return Optional.of(new AuthenticatedUser(userId, role));
        } catch (ParseException | JOSEException | IllegalArgumentException | NullPointerException exception) {
            // 위조/변형된 토큰은 조용히 인증 실패로 처리한다. 토큰 값을 로그에 남기지 않는다.
            return Optional.empty();
        }
    }
}
