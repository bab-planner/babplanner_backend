package com.babplanner.backend.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.babplanner.backend.allergy.repository.UserAllergyRepository;
import com.babplanner.backend.auth.repository.PhoneVerificationRepository;
import com.babplanner.backend.auth.repository.RefreshSessionRepository;
import com.babplanner.backend.inquiry.repository.InquiryRepository;
import com.babplanner.backend.reward.repository.CouponRepository;
import com.babplanner.backend.reward.repository.RewardAccountRepository;
import com.babplanner.backend.reward.repository.RewardTransactionRepository;
import com.babplanner.backend.survey.repository.SurveyResponseRepository;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import com.babplanner.backend.user.repository.UserRepository;
import com.babplanner.backend.waste.repository.WasteSubmissionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    @Autowired protected CouponRepository couponRepository;
    @Autowired protected RewardAccountRepository rewardAccountRepository;
    @Autowired protected RewardTransactionRepository rewardTransactionRepository;
    @Autowired protected SurveyResponseRepository surveyResponseRepository;
    @Autowired protected InquiryRepository inquiryRepository;
    @Autowired protected WasteSubmissionRepository wasteSubmissionRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    @Autowired private JdbcTemplate sampleDataJdbc;

    /**
     * V13/V15가 넣었다가 V29가 운영에서 지우는 샘플 공지/혼잡도를 테스트용으로 되살린다.
     *
     * <p>기존 테스트가 "시드된 공지/혼잡도가 있다"는 가정 위에 쓰여 있어서다. 운영 동작이
     * 아니라 테스트 고정값이며, V29 자체는 SampleDataCleanupMigrationTest가 검증한다.
     */
    private void restoreSampleData() {
        sampleDataJdbc.update("delete from notices where title like '[개발 샘플]%'");
        String[][] notices = {
            {"[개발 샘플] 시스템 점검 안내", "TRUE", "2026-08-20 01:00:00"},
            {"[개발 샘플] 중요 공지 두 번째", "TRUE", "2026-08-24 01:00:00"},
            {"[개발 샘플] 일반 공지 최신", "FALSE", "2026-08-25 01:00:00"},
            {"[개발 샘플] 일반 공지 이전", "FALSE", "2026-08-18 01:00:00"},
        };
        for (String[] n : notices) {
            sampleDataJdbc.update("insert into notices (title, content, important, published_at,"
                    + " created_at, updated_at) values (?, CONCAT('샘플 공지입니다.', CHAR(10), CHAR(10),"
                    + " '두 번째 문단입니다.'), " + n[1] + ", TIMESTAMP '" + n[2] + "',"
                    + " TIMESTAMP '" + n[2] + "', TIMESTAMP '" + n[2] + "')", n[0]);
        }
        String ts = "TIMESTAMP '2026-08-26 02:30:00'";
        sampleDataJdbc.update("insert into congestion_status (site_id, level, people_count,"
                + " display_message, source, observed_at, updated_at) select s.id, 'NORMAL', 42,"
                + " '5분 이내', 'DEV_SEED', " + ts + ", " + ts + " from dining_sites s"
                + " where s.code = 'STUDENT' and not exists (select 1 from congestion_status c"
                + " where c.site_id = s.id)");
        sampleDataJdbc.update("insert into congestion_status (site_id, level, people_count,"
                + " display_message, source, observed_at, updated_at) select s.id, 'COMFORTABLE', 8,"
                + " NULL, 'DEV_SEED', " + ts + ", " + ts + " from dining_sites s"
                + " where s.code = 'STAFF' and not exists (select 1 from congestion_status c"
                + " where c.site_id = s.id)");
    }

    @BeforeEach
    void resetState() {
        restoreSampleData();
        // users를 참조하는 자식 테이블을 먼저 비운다. FK 때문에 순서가 중요하다.
        wasteSubmissionRepository.deleteAll();
        inquiryRepository.deleteAll();
        surveyResponseRepository.deleteAll();
        couponRepository.deleteAll();
        rewardTransactionRepository.deleteAll();
        rewardAccountRepository.deleteAll();
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

    protected static final String ADMIN_PHONE = "01055556666";
    protected static final String ADMIN_STUDENT_ID = "20200001";

    /**
     * 관리자 계정을 만들고 로그인한다.
     *
     * <p>회원가입으로는 관리자를 만들 수 없으므로 저장소에 직접 넣는다. 그것이
     * 이 제품의 실제 운영 방식이기도 하다.
     */
    protected JsonNode adminAuth() throws Exception {
        return adminAuth(ADMIN_PHONE, ADMIN_STUDENT_ID);
    }

    protected JsonNode adminAuth(String phone, String studentId) throws Exception {
        userRepository.save(new UserEntity(
                "관리자",
                phone,
                studentId,
                null,
                Role.ADMIN,
                passwordEncoder.encode(PASSWORD),
                clock.instant()));
        MvcResult result =
                postJson("/api/v1/auth/login", Map.of("phone", phone, "password", PASSWORD));
        return body(result).path("data");
    }

    protected String adminToken() throws Exception {
        return accessTokenOf(adminAuth());
    }
}
