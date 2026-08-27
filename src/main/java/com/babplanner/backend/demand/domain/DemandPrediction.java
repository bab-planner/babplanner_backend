package com.babplanner.backend.demand.domain;

import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.site.domain.DiningSite;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;

/**
 * 한 번의 예측 실행 결과.
 *
 * <p>같은 끼니를 다시 예측하면 새 행이 생긴다. 덮어쓰지 않는 이유는, 지난
 * 예측이 사라지면 "그때 무엇을 보고 그렇게 발주했는가"를 확인할 수 없기
 * 때문이다.
 *
 * <p>기록된 사실이므로 저장한 뒤에는 고치지 않는다. setter를 두지 않은 것도
 * 그래서다.
 *
 * <p>{@code modelVersion}이 {@code fastapi-fixture-}로 시작하면 개발용
 * fixture가 낸 값이다. 예측이 아니다.
 */
@Entity
@Table(name = "demand_predictions")
public class DemandPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private DiningSite site;

    /**
     * 급식 제공일.
     *
     * <p>관리자가 명시한 날짜를 그대로 저장한다. 서버 시간대로 다시 해석하지
     * 않는다. 요청에 적힌 날짜와 저장된 날짜가 다르면 아무도 설명할 수 없다.
     */
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Column(name = "predicted_people", nullable = false)
    private int predictedPeople;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    /** 한 번의 실행을 가리키는 상관 ID. 사용자 식별자가 아니다. */
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DemandPrediction() {
        // JPA 전용
    }

    public DemandPrediction(
            DiningSite site,
            LocalDate serviceDate,
            MealType mealType,
            int predictedPeople,
            String modelVersion,
            String requestId,
            Instant createdAt) {
        this.site = site;
        this.serviceDate = serviceDate;
        this.mealType = mealType;
        this.predictedPeople = predictedPeople;
        this.modelVersion = modelVersion;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public DiningSite getSite() {
        return site;
    }

    public LocalDate getServiceDate() {
        return serviceDate;
    }

    public MealType getMealType() {
        return mealType;
    }

    public int getPredictedPeople() {
        return predictedPeople;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public String getRequestId() {
        return requestId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
