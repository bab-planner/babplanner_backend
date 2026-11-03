package com.babplanner.backend.cors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;

import com.babplanner.backend.support.ApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

/** 설정이 없으면 어떤 브라우저 Origin도 허용되지 않는다. */
class CorsDefaultEmptyTest extends ApiIntegrationTest {

    @Test
    @DisplayName("허용 목록이 비어 있으면 preflight에 CORS 헤더가 없다")
    void emptyAllowList() throws Exception {
        var res = perform(options("/api/v1/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://admin.example.app")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .getResponse();
        assertThat(res.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isNull();
    }
}
