package com.babplanner.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** SMS 모드가 noop이 아니면 developmentCode 키가 응답에 없어야 한다. */
@TestPropertySource(properties = "bapplanner.sms.mode=real-provider")
class DevelopmentCodeTest extends ApiIntegrationTest {

    @Test
    @DisplayName("noop이 아니면 developmentCode가 응답에 없다")
    void absentOutsideNoop() throws Exception {
        var result = postJson("/api/v1/auth/phone/send-code", Map.of("phone", PHONE));

        JsonNode data = body(result).path("data");
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(data.has("developmentCode")).isFalse();
        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(smsSender.lastCodeFor(PHONE));
    }
}
