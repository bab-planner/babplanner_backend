package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.waste.analysis.FakeWasteAnalyzer;
import com.babplanner.backend.waste.analysis.WasteAnalysisDecision;
import com.babplanner.backend.waste.analysis.WasteAnalysisInput;
import com.babplanner.backend.waste.analysis.WasteAnalysisResult;
import com.babplanner.backend.waste.storage.LocalWasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 개발용 분석기.
 *
 * <p>여기서 확인하는 값은 개발 fixture일 뿐 AI 분석 결과가 아니다.
 */
class FakeWasteAnalyzerTest {

    @TempDir Path root;

    private String store(byte[] content) {
        return new LocalWasteImageStorage(root)
                .store(new WasteImageUpload(
                        () -> new ByteArrayInputStream(content),
                        "a.jpg",
                        "image/jpeg",
                        content.length))
                .storageKey();
    }

    private FakeWasteAnalyzer analyzer() {
        return new FakeWasteAnalyzer(new LocalWasteImageStorage(root));
    }

    @Test
    @DisplayName("내용이 있는 이미지는 APPROVED다")
    void approvesNonEmptyImage() {
        String key = store("image".getBytes(StandardCharsets.UTF_8));

        WasteAnalysisResult result = analyzer().analyze(new WasteAnalysisInput(1L, 2L, key));

        assertThat(result.decision()).isEqualTo(WasteAnalysisDecision.APPROVED);
    }

    @Test
    @DisplayName("결과 값이 계약 범위(0~1) 안이다")
    void resultIsWithinContractRange() {
        String key = store("image".getBytes(StandardCharsets.UTF_8));

        WasteAnalysisResult result = analyzer().analyze(new WasteAnalysisInput(1L, 2L, key));

        assertThat(result.wasteRate()).isBetween(0.0, 1.0);
        assertThat(result.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("빈 이미지는 REJECTED다")
    void rejectsEmptyImage() {
        String key = store(new byte[0]);

        WasteAnalysisResult result = analyzer().analyze(new WasteAnalysisInput(1L, 2L, key));

        assertThat(result.decision()).isEqualTo(WasteAnalysisDecision.REJECTED);
        assertThat(result.wasteRate()).isNull();
        assertThat(result.confidence()).isNull();
    }

    @Test
    @DisplayName("읽을 수 없는 이미지는 실패로 전달된다")
    void missingImageFails() {
        assertThatThrownBy(() -> analyzer()
                        .analyze(new WasteAnalysisInput(
                                1L, 2L, "00000000000000000000000000000000")))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("모델 버전이 이 프로세스의 fixture임을 드러낸다")
    void modelVersionNamesThisProcess() {
        String key = store("image".getBytes(StandardCharsets.UTF_8));

        WasteAnalysisResult result = analyzer().analyze(new WasteAnalysisInput(1L, 2L, key));

        // 분석 서비스 쪽 fixture와 이름이 같으면 저장된 결과만 보고
        // 어느 쪽을 거쳐 왔는지 알 수 없다.
        assertThat(result.modelVersion()).isEqualTo("spring-fixture-waste-v1");
        assertThat(result.modelVersion()).isNotEqualTo("fastapi-fixture-waste-v1");
        assertThat(result.modelVersion()).contains("fixture");
    }

    @Test
    @DisplayName("거절된 결과에도 모델 버전이 있다")
    void rejectedResultAlsoCarriesModelVersion() {
        String key = store(new byte[0]);

        WasteAnalysisResult result = analyzer().analyze(new WasteAnalysisInput(1L, 2L, key));

        assertThat(result.decision()).isEqualTo(WasteAnalysisDecision.REJECTED);
        assertThat(result.modelVersion()).isEqualTo("spring-fixture-waste-v1");
    }

    @Test
    @DisplayName("결과에 리워드 필드가 없다")
    void resultCarriesNoRewardFields() {
        // record component 이름으로 고정한다. 분석기가 지급을 결정하지 않는다.
        assertThat(java.util.Arrays.stream(WasteAnalysisResult.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList())
                .containsExactly("decision", "wasteRate", "confidence", "modelVersion")
                .doesNotContain("earnedPoints", "couponIssued");
    }

    @Test
    @DisplayName("입력에 서버 파일 경로가 들어가지 않는다")
    void inputCarriesOnlyStorageKey() {
        assertThat(java.util.Arrays.stream(WasteAnalysisInput.class.getRecordComponents())
                        .map(java.lang.reflect.RecordComponent::getName)
                        .toList())
                .containsExactly("submissionId", "menuId", "imageStorageKey");
    }
}
