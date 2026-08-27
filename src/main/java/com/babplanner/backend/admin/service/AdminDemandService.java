package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.DemandPredictionRequest;
import com.babplanner.backend.admin.dto.DemandPredictionResponse;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.demand.client.DemandPredictionResult;
import com.babplanner.backend.demand.client.DemandPredictor;
import com.babplanner.backend.demand.domain.DemandPrediction;
import com.babplanner.backend.demand.repository.DemandPredictionRepository;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.site.domain.DiningSite;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 수요 예측.
 *
 * <p>조회는 AI 서비스와 무관하다. 이미 저장된 이력을 보는 일이므로 예측
 * 서비스가 꺼져 있어도 계속 동작한다.
 *
 * <p>실행은 AI 서비스가 성공했을 때만 행을 남긴다. 실패한 실행을 저장하면
 * 나중에 목록에서 그 행이 무엇인지 알 수 없다.
 */
@Service
public class AdminDemandService {

    private final DemandPredictionRepository predictionRepository;
    private final DiningSiteRepository siteRepository;
    private final DemandPredictor predictor;
    private final Clock clock;

    public AdminDemandService(
            DemandPredictionRepository predictionRepository,
            DiningSiteRepository siteRepository,
            DemandPredictor predictor,
            Clock clock) {
        this.predictionRepository = predictionRepository;
        this.siteRepository = siteRepository;
        this.predictor = predictor;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<DemandPredictionResponse> getPredictions(
            Long siteId, LocalDate date, MealType mealType) {
        requireSite(siteId);

        List<DemandPrediction> predictions = mealType == null
                ? predictionRepository.findBySiteIdAndServiceDateOrderByCreatedAtDescIdDesc(
                        siteId, date)
                : predictionRepository
                        .findBySiteIdAndServiceDateAndMealTypeOrderByCreatedAtDescIdDesc(
                                siteId, date, mealType);
        return predictions.stream().map(DemandPredictionResponse::from).toList();
    }

    /**
     * 예측을 한 번 실행한다.
     *
     * <p>같은 끼니를 다시 실행하면 기존 행을 덮지 않고 새 행이 생긴다. 지난
     * 예측이 사라지면 그때 무엇을 보고 발주했는지 확인할 수 없다.
     */
    @Transactional
    public DemandPredictionResponse predict(DemandPredictionRequest request) {
        DiningSite site = requireSite(request.siteId());

        String requestId = newRequestId();

        DemandPredictionResult result;
        try {
            result = predictor.predict(
                    requestId, site.getId(), request.serviceDate(), request.mealType());
        } catch (AiServiceException exception) {
            // 원인은 서버 로그에만 남기고 관리자에게는 "지금은 실행할 수 없다"만
            // 알린다. 연결 실패인지 계약 위반인지는 화면이 알 수 있는 것보다
            // 많은 구분이다.
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        return DemandPredictionResponse.from(predictionRepository.save(new DemandPrediction(
                site,
                // 관리자가 적은 날짜를 그대로 저장한다. 서버 시간대로 다시
                // 해석하지 않는다.
                request.serviceDate(),
                request.mealType(),
                result.predictedPeople(),
                result.modelVersion(),
                requestId,
                clock.instant())));
    }

    private DiningSite requireSite(Long siteId) {
        return siteRepository
                .findById(siteId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_DINING_SITE));
    }

    /**
     * 실행 하나를 가리키는 상관 ID.
     *
     * <p>사용자 식별자가 아니다. 휴대폰 번호, 학번, 사용자 id, 토큰을 넣지
     * 않는다. AI 서비스는 이 실행을 누가 눌렀는지 알 필요가 없다.
     */
    private static String newRequestId() {
        return "demand-" + UUID.randomUUID();
    }
}
