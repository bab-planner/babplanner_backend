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
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AccessTokenAuthenticationFilter accessTokenAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            AccessTokenAuthenticationFilter accessTokenAuthenticationFilter,
            ObjectMapper objectMapper) {
        this.accessTokenAuthenticationFilter = accessTokenAuthenticationFilter;
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
                        // OpenAPI에 security가 없는 공개 endpoint
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/phone/send-code",
                                "/api/v1/auth/phone/verify",
                                "/api/v1/auth/signup",
                                "/api/v1/auth/login",
                                "/api/v1/auth/refresh")
                        .permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/allergies")
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
                        accessTokenAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
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
