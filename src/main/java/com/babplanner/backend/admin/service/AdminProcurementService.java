package com.babplanner.backend.admin.service;

import com.babplanner.backend.admin.dto.PlannedIngredientRequest;
import com.babplanner.backend.admin.dto.ProcurementItemResponse;
import com.babplanner.backend.admin.dto.ProcurementRunRequest;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.demand.domain.DemandPrediction;
import com.babplanner.backend.demand.repository.DemandPredictionRepository;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.procurement.client.PlannedIngredient;
import com.babplanner.backend.procurement.client.ProcurementRecommendationResult;
import com.babplanner.backend.procurement.client.ProcurementRecommender;
import com.babplanner.backend.procurement.domain.ProcurementRun;
import com.babplanner.backend.procurement.repository.ProcurementRunRepository;
import com.babplanner.backend.site.domain.DiningSite;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 관리자 발주 추천.
 *
 * <p>조회는 저장된 실행을 그대로 보여 준다. AI 서비스가 꺼져 있어도 동작한다.
 *
 * <p>실행은 예측이 있어야 한다. 관리자가 인원을 직접 보낼 수 없게 해 둔 것과
 * 같은 이유다. 근거 없는 추천을 만들지 않는다.
 */
@Service
public class AdminProcurementService {

    private final ProcurementRunRepository runRepository;
    private final DemandPredictionRepository predictionRepository;
    private final DiningSiteRepository siteRepository;
    private final ProcurementRecommender recommender;
    private final Clock clock;

    public AdminProcurementService(
            ProcurementRunRepository runRepository,
            DemandPredictionRepository predictionRepository,
            DiningSiteRepository siteRepository,
            ProcurementRecommender recommender,
            Clock clock) {
        this.runRepository = runRepository;
        this.predictionRepository = predictionRepository;
        this.siteRepository = siteRepository;
        this.recommender = recommender;
        this.clock = clock;
    }

    /**
     * 가장 최근 실행의 항목.
     *
     * <p>여러 실행을 섞지 않는다. 섞이면 같은 재료가 두 줄 나오고 어느 쪽이
     * 최신인지 알 수 없다. 실행이 없으면 빈 목록이다.
     */
    @Transactional(readOnly = true)
    public List<ProcurementItemResponse> getRecommendations(
            Long siteId, LocalDate date, MealType mealType) {
        requireSite(siteId);

        return runRepository
                .findFirstBySiteIdAndServiceDateAndMealTypeOrderByCreatedAtDescIdDesc(
                        siteId, date, mealType)
                .map(run -> run.getItems().stream()
                        .map(ProcurementItemResponse::from)
                        .toList())
                .orElseGet(List::of);
    }

    /**
     * 추천을 한 번 실행한다.
     *
     * <p>실행과 항목이 한 트랜잭션에서 같이 저장된다. 실행만 남고 항목이
     * 일부만 있으면, 그 목록을 보고 발주하는 사람은 빠진 재료를 사지 않는다.
     *
     * <p>AI 호출이 실패하면 아무것도 저장하지 않는다. 지난 성공 실행은 그대로
     * 남는다.
     */
    @Transactional
    public List<ProcurementItemResponse> recommend(ProcurementRunRequest request) {
        DiningSite site = requireSite(request.siteId());
        List<PlannedIngredient> planned = plannedOf(request.plannedIngredients());

        DemandPrediction prediction = predictionRepository
                .findFirstBySiteIdAndServiceDateAndMealTypeOrderByCreatedAtDescIdDesc(
                        site.getId(), request.serviceDate(), request.mealType())
                // 예측 없이 추천하면 무엇을 근거로 그 수량이 나왔는지 아무도
                // 설명할 수 없다.
                .orElseThrow(() -> new ApiException(ErrorCode.DEMAND_PREDICTION_REQUIRED));

        String requestId = newRequestId();

        ProcurementRecommendationResult result;
        try {
            result = recommender.recommend(
                    requestId,
                    site.getId(),
                    request.serviceDate(),
                    request.mealType(),
                    prediction.getPredictedPeople(),
                    planned);
        } catch (AiServiceException exception) {
            // 원인은 서버 로그에만 남긴다. 관리자에게는 지금 실행할 수 없다는
            // 사실만 알린다.
            throw new ApiException(ErrorCode.AI_SERVICE_UNAVAILABLE);
        }

        ProcurementRun run = new ProcurementRun(
                site,
                // 관리자가 적은 날짜를 그대로 저장한다.
                request.serviceDate(),
                request.mealType(),
                prediction,
                result.modelVersion(),
                requestId,
                clock.instant());
        result.recommendations()
                .forEach(item -> run.addItem(
                        item.ingredientName(),
                        item.unit(),
                        item.plannedQuantity(),
                        item.recommendedQuantity(),
                        item.adjustmentRate(),
                        item.reason()));

        return runRepository.save(run).getItems().stream()
                .map(ProcurementItemResponse::from)
                .toList();
    }

    /**
     * 요청 목록을 검증해 옮긴다.
     *
     * <p>이름과 단위가 같은 줄이 두 번 오면 거절한다. 그대로 두면 응답의 어느
     * 줄이 어느 요청 줄인지 짝지을 수 없고, 관리자도 같은 재료를 두 번 사게
     * 된다. 대소문자와 앞뒤 공백은 같은 것으로 본다.
     */
    private List<PlannedIngredient> plannedOf(List<PlannedIngredientRequest> requested) {
        Set<String> seen = new HashSet<>();
        List<PlannedIngredient> planned = new ArrayList<>();

        for (PlannedIngredientRequest item : requested) {
            PlannedIngredient converted = new PlannedIngredient(
                    item.normalizedName(), item.normalizedUnit(), item.plannedQuantity());
            if (converted.ingredientName().isBlank() || converted.unit().isBlank()) {
                throw new ApiException(ErrorCode.INVALID_PROCUREMENT_REQUEST);
            }
            if (!seen.add(converted.matchKey())) {
                throw new ApiException(ErrorCode.INVALID_PROCUREMENT_REQUEST);
            }
            planned.add(converted);
        }
        return List.copyOf(planned);
    }

    private DiningSite requireSite(Long siteId) {
        return siteRepository
                .findById(siteId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNKNOWN_DINING_SITE));
    }

    /** 사용자 식별자가 아니다. AI 서비스는 누가 눌렀는지 알 필요가 없다. */
    private static String newRequestId() {
        return "procurement-" + UUID.randomUUID();
    }
}
