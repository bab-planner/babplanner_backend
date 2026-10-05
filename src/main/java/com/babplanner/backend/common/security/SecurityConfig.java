package com.babplanner.backend.common.security;

import com.babplanner.backend.common.api.ApiError;
import com.babplanner.backend.common.api.ApiResponse;
import com.babplanner.backend.common.error.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * 경로별 접근 정책.
 *
 * <p>OpenAPI의 security 정의를 그대로 옮긴다. /api/v1/** 전체를 인증 필수로
 * 묶지 않는다. 공개 endpoint는 명시적으로 허용한다.
 *
 * <p>권한 판단은 여기서만 한다. 컨트롤러가 각자 principal을 보고 판단하면
 * 규칙이 흩어지고, 한 곳을 빠뜨렸을 때 그 endpoint만 조용히 열린다.
 *
 * <p>역할의 출처는 검증된 JWT claim뿐이다. 요청 본문이나 헤더로 들어온
 * role 값은 쓰지 않는다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AccessTokenAuthenticationFilter accessTokenAuthenticationFilter;
    private final ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            AccessTokenAuthenticationFilter accessTokenAuthenticationFilter,
            ServiceTokenAuthenticationFilter serviceTokenAuthenticationFilter,
            ObjectMapper objectMapper) {
        this.accessTokenAuthenticationFilter = accessTokenAuthenticationFilter;
        this.serviceTokenAuthenticationFilter = serviceTokenAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 서비스 간 호출 전용. 사람이 쓰는 경로가 아니다.
                        //
                        // 사람 역할(ROLE_*)이 아니라 별도 권한을 요구한다.
                        // 그래서 ADMIN JWT만 들고 와도 들어올 수 없다. 관리자
                        // 계정 하나가 새면 수집 경로까지 열리는 구조를 만들지
                        // 않는다.
                        .requestMatchers("/internal/v1/**")
                        .hasAuthority(ServiceTokenAuthenticationFilter.INTERNAL_SERVICE_AUTHORITY)
                        // 관리자 전용. 아래 공개 규칙보다 먼저 둔다. 나중에
                        // 공개 규칙이 늘어나다 admin 경로까지 걸리면 관리 API가
                        // 통째로 열린다.
                        //
                        // 익명은 401, 로그인한 일반 사용자는 403이다. 있는지
                        // 없는지가 아니라 권한이 없다는 뜻이라야 한다.
                        .requestMatchers("/api/v1/admin/**")
                        .hasRole("ADMIN")
                        // 회원 탈퇴는 일반 사용자 본인만. 관리자 계정은 이 경로로 지울 수 없다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/users/me/deletion")
                        .hasRole("USER")
                        // OpenAPI에 security가 없는 공개 endpoint
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/phone/send-code",
                                "/api/v1/auth/phone/verify",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh")
                        .permitAll()
                        // OpenAPI에 security가 없는 공개 조회 endpoint.
                        // 메서드를 GET으로 못박아 향후 쓰기 API가 딸려 열리지 않게 한다.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/api/v1/menus",
                                "/api/v1/menus/**",
                                "/api/v1/congestion/current",
                                "/api/v1/notices",
                                "/api/v1/notices/**",
                                "/api/v1/allergies")
                        .permitAll()
                        // 나머지는 bearerAuth 필요
                        .anyRequest()
                        .authenticated())
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint((request, response, exception) ->
                                writeError(response, ErrorCode.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, exception) ->
                                writeError(response, ErrorCode.FORBIDDEN)))
                .addFilterBefore(
                        accessTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(
                        serviceTokenAuthenticationFilter,
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** 인증 실패도 동일한 ErrorEnvelope로 내려보낸다. */
    private void writeError(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                ApiResponse.failure(ApiError.of(code.name(), code.message())));
    }
}
