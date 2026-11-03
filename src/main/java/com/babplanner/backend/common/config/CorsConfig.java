package com.babplanner.backend.common.config;

import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 관리자 웹(Vercel 등 별도 출처)용 CORS. SecurityConfig가 이 source를 사용한다.
 *
 * <p>/api/v1/** 에만 적용하고 /internal/v1/** 는 등록하지 않는다. 허용 Origin은 설정에
 * 적은 값과 정확히 일치할 때만이고, 와일드카드나 Origin 반사는 하지 않는다. 인증은 Bearer
 * 헤더라 쿠키가 필요 없으므로 credentials는 끈다. CORS는 브라우저의 읽기 제한일 뿐
 * 접근 통제가 아니다. 접근 통제는 SecurityConfig의 인가 규칙이 한다.
 */
@Configuration
public class CorsConfig {

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties properties) {
        List<String> origins = properties.parsedOrigins();
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        source.registerCorsConfiguration("/api/v1/**", config);
        return source;
    }
}
