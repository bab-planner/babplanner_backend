package com.babplanner.backend.common.security;

import com.babplanner.backend.common.config.InternalServiceProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * X-Service-Token 헤더를 보고 내부 서비스 권한을 부여한다.
 *
 * <p>사용자 인증이 아니다. 다른 프로세스가 내부 수집 경로를 부를 수 있게
 * 하는 공유 토큰이며, 사용자 JWT와는 권한이 섞이지 않는다. 사람 계정이
 * 아니므로 principal에 사용자 정보를 담지 않는다.
 *
 * <p>토큰이 없거나 틀리면 컨텍스트를 비운 채 통과시킨다. 거절은 경로 규칙이
 * 한다. 헤더 값을 로그로 남기지 않는다.
 */
@Component
public class ServiceTokenAuthenticationFilter extends OncePerRequestFilter {

    public static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    /** 사람 역할(ROLE_*)과 이름 공간을 나눈다. ADMIN이 여기로 들어올 수 없다. */
    public static final String INTERNAL_SERVICE_AUTHORITY = "INTERNAL_SERVICE";

    /** principal 자리에 둘 이름. 사용자 식별자가 아니다. */
    private static final String PRINCIPAL = "internal-service";

    private final InternalServiceProperties properties;

    public ServiceTokenAuthenticationFilter(InternalServiceProperties properties) {
        // 기동 시점에 확인한다. 첫 요청이 올 때까지 미루면 잘못 뜬 서버가
        // 정상으로 보인다.
        properties.requiredToken();
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (matches(request.getHeader(SERVICE_TOKEN_HEADER))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    PRINCIPAL,
                    null,
                    List.of(new SimpleGrantedAuthority(INTERNAL_SERVICE_AUTHORITY)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }

    /**
     * 토큰을 상수 시간으로 비교한다.
     *
     * <p>{@code String.equals}는 첫 다른 바이트에서 멈춘다. 그 시간 차이로
     * 토큰을 한 글자씩 맞춰 나갈 수 있다.
     */
    private boolean matches(String presented) {
        if (presented == null) {
            return false;
        }
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8),
                properties.getServiceToken().getBytes(StandardCharsets.UTF_8));
    }
}
