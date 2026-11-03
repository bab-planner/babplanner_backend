package com.babplanner.backend.common.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 브라우저(관리자 웹)에서 /api/v1/** 를 부를 수 있는 Origin 허용 목록.
 *
 * <p>기본값은 빈 목록이다. 비어 있으면 어떤 브라우저 Origin도 허용하지 않는다.
 * 쉼표로 여러 개를 줄 수 있고, 정확히 일치하는 {@code https://host[:port]} 만 받는다.
 * 와일드카드, http, 경로, 끝 슬래시는 기동 시점에 거절한다. 조용히 무시하면 설정이
 * 적용됐다고 착각한 채 배포하게 된다.
 */
@ConfigurationProperties(prefix = "bapplanner.cors")
public class CorsProperties {

    private static final Pattern HTTPS_ORIGIN =
            Pattern.compile("^https://[a-z0-9]([a-z0-9-]*[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]*[a-z0-9])?)*(:\\d{1,5})?$");

    private String allowedOrigins = "";

    public String getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(String allowedOrigins) {
        this.allowedOrigins = allowedOrigins == null ? "" : allowedOrigins;
    }

    /**
     * 검증된 Origin 목록.
     *
     * @throws IllegalStateException 형식이 허용되지 않는 값이 있는 경우
     */
    public List<String> parsedOrigins() {
        List<String> origins = new ArrayList<>();
        for (String raw : allowedOrigins.split(",")) {
            String origin = raw.trim().toLowerCase(java.util.Locale.ROOT);
            if (origin.isEmpty()) {
                continue;
            }
            if (!HTTPS_ORIGIN.matcher(origin).matches()) {
                // 잘못된 값 자체는 남기지 않는다.
                throw new IllegalStateException(
                        "bapplanner.cors.allowed-origins 는 정확한 https://host[:port] 만 "
                                + "쉼표로 구분해 허용합니다. 와일드카드, http, 경로, 끝 슬래시는 "
                                + "쓸 수 없습니다.");
            }
            if (!origins.contains(origin)) {
                origins.add(origin);
            }
        }
        return List.copyOf(origins);
    }
}
