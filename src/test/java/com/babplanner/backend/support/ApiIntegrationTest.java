package com.babplanner.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.babplanner.backend.allergy.repository.UserAllergyRepository;
import com.babplanner.backend.auth.repository.PhoneVerificationRepository;
import com.babplanner.backend.auth.repository.RefreshSessionRepository;
import com.babplanner.backend.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * MockMvc 기반 통합 테스트 기반 클래스.
 *
 * <p>H2 인메모리 DB와 Flyway migration을 그대로 사용해 실제 스키마를 검증한다.
 * 외부 네트워크 호출은 없다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@org.springframework.context.annotation.Import(TestSupportConfig.class)
public abstract class ApiIntegrationTest {

    protected static final String PHONE = "01012345678";
    protected static final String STUDENT_ID = "20201234";
    protected static final String PASSWORD = "test1234!";

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected FakeSmsSender smsSender;
    @Autowired protected MutableTestClock clock;

    @Autowired protected UserRepository userRepository;
    @Autowired protected RefreshSessionRepository refreshSessionRepository;
    @Autowired protected PhoneVerificationRepository phoneVerificationRepository;
    @Autowired protected UserAllergyRepository userAllergyRepository;

    @BeforeEach
    void resetState() {
        userAllergyRepository.deleteAll();
        refreshSessionRepository.deleteAll();
        phoneVerificationRepository.deleteAll();
        userRepository.deleteAll();
        smsSender.clear();
        clock.reset();
    }

    protected MvcResult perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder).andReturn();
    }

    protected MvcResult postJson(String path, Object body) throws Exception {
        return perform(post(path)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected MvcResult postJson(String path, Object body, String accessToken) throws Exception {
        return perform(post(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected MvcResult getJson(String path) throws Exception {
        return perform(get(path));
    }

    protected MvcResult getJson(String path, String accessToken) throws Exception {
        return perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    protected MvcResult patchJson(String path, Object body, String accessToken) throws Exception {
        return perform(patch(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected MvcResult putJson(String path, Object body, String accessToken) throws Exception {
        return perform(put(path)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)));
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    /** send-code → verify 까지 마치고 회원가입용 verificationToken을 돌려준다. */
    protected String verifiedToken(String phone) throws Exception {
        postJson("/api/v1/auth/phone/send-code", Map.of("phone", phone));
        String code = smsSender.lastCodeFor(phone);
        MvcResult result = postJson("/api/v1/auth/phone/verify", Map.of("phone", phone, "code", code));
        return body(result).path("data").path("verificationToken").asText();
    }

    protected Map<String, Object> signupPayload(String phone, String studentId, String token) {
        return Map.of(
                "name", "홍길동",
                "phone", phone,
                "studentId", studentId,
                "password", PASSWORD,
                "agreements", Map.of("serviceTerms", true, "privacyPolicy", true),
                "verificationToken", token);
    }

    /** 기본 사용자를 가입시키고 AuthData 노드를 돌려준다. */
    protected JsonNode signup() throws Exception {
        return signup(PHONE, STUDENT_ID);
    }

    protected JsonNode signup(String phone, String studentId) throws Exception {
        String token = verifiedToken(phone);
        MvcResult result = postJson("/api/v1/auth/signup", signupPayload(phone, studentId, token));
        return body(result).path("data");
    }

    protected String accessTokenOf(JsonNode authData) {
        return authData.path("accessToken").asText();
    }
}
