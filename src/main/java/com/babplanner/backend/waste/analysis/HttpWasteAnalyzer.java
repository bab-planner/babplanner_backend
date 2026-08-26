package com.babplanner.backend.waste.analysis;

import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageStorageException;
import java.io.IOException;
import java.io.InputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 별도 프로세스로 떠 있는 분석 서비스를 호출한다.
 *
 * <p>이미지는 저장소에서 읽어 바이트로 보낸다. 분석 서비스에 저장소 경로나
 * 자격증명을 주지 않고, 공개 이미지 URL도 만들지 않기 위해서다.
 *
 * <p>보내는 것은 상관 ID, 메뉴 id, 이미지뿐이다. 사용자 식별 정보는 분석에
 * 필요하지 않으므로 넘기지 않는다.
 *
 * <p>응답을 그대로 믿지 않는다. 계약을 벗어난 응답은 잘못된 결과를 제출에
 * 저장하느니 실패로 끝낸다.
 */
public class HttpWasteAnalyzer implements WasteAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(HttpWasteAnalyzer.class);

    static final String ANALYZE_PATH = "/internal/v1/ai/waste/analyze";
    static final String SERVICE_TOKEN_HEADER = "X-Service-Token";

    private final RestClient restClient;
    private final WasteImageStorage imageStorage;
    private final String serviceToken;

    public HttpWasteAnalyzer(
            RestClient restClient, WasteImageStorage imageStorage, String serviceToken) {
        this.restClient = restClient;
        this.imageStorage = imageStorage;
        this.serviceToken = serviceToken;
    }

    @Override
    public WasteAnalysisResult analyze(WasteAnalysisInput input) {
        String requestId = correlationIdFor(input.submissionId());
        byte[] image = readImage(input.imageStorageKey());

        AiWasteResponse response;
        try {
            response = restClient
                    .post()
                    .uri(ANALYZE_PATH)
                    .header(SERVICE_TOKEN_HEADER, serviceToken)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(multipartBody(requestId, input.menuId(), image))
                    .retrieve()
                    .body(AiWasteResponse.class);
        } catch (RestClientException exception) {
            // 상태 코드는 남기되 본문이나 헤더는 남기지 않는다. 토큰과 이미지가
            // 로그로 새면 안 된다.
            log.warn("잔반 분석 요청이 실패했습니다. requestId={}", requestId);
            throw new WasteAnalysisException("분석 서비스를 호출하지 못했습니다.", exception);
        }

        return validated(requestId, response);
    }

    /**
     * 제출 한 건을 가리키는 상관 ID.
     *
     * <p>사용자 식별자가 아니다. 휴대폰 번호, 학번, 사용자 id, 토큰을 넣지
     * 않는다.
     */
    public static String correlationIdFor(long submissionId) {
        return "waste-" + submissionId;
    }

    private byte[] readImage(String storageKey) {
        try (InputStream stream = imageStorage.read(storageKey)) {
            return stream.readAllBytes();
        } catch (IOException | WasteImageStorageException exception) {
            throw new WasteAnalysisException("이미지를 읽지 못했습니다.", exception);
        }
    }

    private MultiValueMap<String, Object> multipartBody(
            String requestId, long menuId, byte[] image) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("requestId", requestId);
        body.add("menuId", String.valueOf(menuId));
        // 파일 이름은 계약에 필요하지 않다. 사용자가 올린 원본 이름을 그대로
        // 흘려보내지 않기 위해 고정된 이름을 쓴다.
        body.add("image", new ByteArrayResource(image) {
            @Override
            public String getFilename() {
                return "image";
            }
        });
        return body;
    }

    /** 계약을 벗어난 응답은 결과로 받아들이지 않는다. */
    private WasteAnalysisResult validated(String requestId, AiWasteResponse response) {
        if (response == null) {
            throw new WasteAnalysisException("분석 서비스가 빈 응답을 돌려줬습니다.");
        }
        if (!requestId.equals(response.requestId())) {
            // 다른 요청의 결과를 이 제출에 저장하면 안 된다.
            throw new WasteAnalysisException("분석 응답의 요청 식별자가 일치하지 않습니다.");
        }
        if (response.modelVersion() == null || response.modelVersion().isBlank()) {
            throw new WasteAnalysisException("분석 응답에 모델 버전이 없습니다.");
        }

        WasteAnalysisDecision decision = decisionOf(response.decision());
        if (decision == WasteAnalysisDecision.APPROVED) {
            requireRatio(response.wasteRate(), "잔반률");
            requireRatio(response.confidence(), "신뢰도");
        } else {
            allowNullRatio(response.wasteRate(), "잔반률");
            allowNullRatio(response.confidence(), "신뢰도");
        }

        return new WasteAnalysisResult(
                decision, response.wasteRate(), response.confidence(), response.modelVersion());
    }

    private WasteAnalysisDecision decisionOf(String raw) {
        if (raw == null) {
            throw new WasteAnalysisException("분석 응답에 판정이 없습니다.");
        }
        try {
            return WasteAnalysisDecision.valueOf(raw);
        } catch (IllegalArgumentException exception) {
            throw new WasteAnalysisException("알 수 없는 분석 판정입니다.", exception);
        }
    }

    private void requireRatio(Double value, String name) {
        if (value == null) {
            throw new WasteAnalysisException("승인된 분석에 " + name + "가 없습니다.");
        }
        allowNullRatio(value, name);
    }

    private void allowNullRatio(Double value, String name) {
        if (value != null && (value < 0 || value > 1 || value.isNaN())) {
            throw new WasteAnalysisException(name + "가 허용 범위를 벗어났습니다.");
        }
    }
}
