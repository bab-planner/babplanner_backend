package com.babplanner.backend.procurement.domain;

import com.babplanner.backend.demand.domain.DemandPrediction;
import com.babplanner.backend.menu.domain.MealType;
import com.babplanner.backend.site.domain.DiningSite;
import jakarta.persistence.CascadeType;
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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * 한 번의 발주 추천 실행.
 *
 * <p>항목은 이 실행에 딸린다. 항목만 따로 저장하면 어느 실행에서 나왔는지,
 * 어떤 예측을 근거로 삼았는지 알 수 없다.
 *
 * <p>실행 전체가 한 트랜잭션에서 저장된다. 실행은 남았는데 항목이 일부만
 * 있으면, 그 목록을 보고 발주한 사람은 빠진 재료를 사지 않는다.
 *
 * <p>{@code modelVersion}이 {@code fastapi-fixture-}로 시작하면 개발용
 * fixture가 낸 값이며 추천이 아니다.
 */
@Entity
@Table(name = "procurement_runs")
public class ProcurementRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private DiningSite site;

    /** 관리자가 명시한 날짜를 그대로 저장한다. 서버 시간대로 다시 해석하지 않는다. */
    @Column(name = "service_date", nullable = false)
    private LocalDate serviceDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    /** 근거로 삼은 예측. 예측 없이 추천하지 않는다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "demand_prediction_id", nullable = false)
    private DemandPrediction demandPrediction;

    @Column(name = "model_version", nullable = false, length = 100)
    private String modelVersion;

    /** 한 번의 실행을 가리키는 상관 ID. 사용자 식별자가 아니다. */
    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "run", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("position ASC")
    private List<ProcurementItem> items = new ArrayList<>();

    protected ProcurementRun() {
        // JPA 전용
    }

    public ProcurementRun(
            DiningSite site,
            LocalDate serviceDate,
            MealType mealType,
            DemandPrediction demandPrediction,
            String modelVersion,
            String requestId,
            Instant createdAt) {
        this.site = site;
        this.serviceDate = serviceDate;
        this.mealType = mealType;
        this.demandPrediction = demandPrediction;
        this.modelVersion = modelVersion;
        this.requestId = requestId;
        this.createdAt = createdAt;
    }

    /** 항목은 실행을 통해서만 붙인다. 자리 번호는 요청에 들어온 순서다. */
    public void addItem(
            String ingredientName,
            String unit,
            double plannedQuantity,
            double recommendedQuantity,
            Double adjustmentRate,
            String reason) {
        items.add(new ProcurementItem(
                this,
                items.size(),
                ingredientName,
                unit,
                plannedQuantity,
                recommendedQuantity,
                adjustmentRate,
                reason));
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

    public DemandPrediction getDemandPrediction() {
        return demandPrediction;
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

    public List<ProcurementItem> getItems() {
        return List.copyOf(items);
    }
}
