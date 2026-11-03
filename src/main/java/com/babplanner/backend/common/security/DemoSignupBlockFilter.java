package com.babplanner.backend.common.security;

import com.babplanner.backend.common.api.ApiError;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 공개 시연 모드에서 휴대폰 인증/회원가입 경로를 403으로 막는다.
 *
 * <p>인가 규칙(denyAll)로 두지 않는 이유: 익명 요청의 접근 거부는 Spring Security가
 * 401로 바꿔 버린다. 여기서는 기존 공통 오류 형식의 403을 그대로 돌려준다.
 *
 * <p>Bean으로 등록하지 않는다(서블릿 필터로 중복 등록되지 않게 SecurityConfig가 만든다).
 * CorsFilter 뒤에 위치하므로 허용된 Origin의 브라우저는 403 본문을 읽을 수 있다.
 */
public class DemoSignupBlockFilter extends OncePerRequestFilter {

    static final Set<String> BLOCKED_PATHS = Set.of(
            "/api/v1/auth/phone/send-code",
            "/api/v1/auth/phone/verify",
            "/api/v1/auth/signup");

    private final ObjectMapper objectMapper;

    public DemoSignupBlockFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("POST".equals(request.getMethod()) && BLOCKED_PATHS.contains(normalizedPath(request))) {
            response.setStatus(ErrorCode.FORBIDDEN.status().value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(
                    response.getWriter(),
                    ApiResponse.failure(
                            ApiError.of(ErrorCode.FORBIDDEN.name(), ErrorCode.FORBIDDEN.message())));
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** 끝 슬래시로 우회하지 못하게 한다. */
    private String normalizedPath(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }
}
