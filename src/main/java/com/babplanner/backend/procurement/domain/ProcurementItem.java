package com.babplanner.backend.procurement.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 추천 한 줄.
 *
 * <p>수량과 단위를 분리해 저장한다. "10kg" 같은 문자열은 읽는 쪽마다 다르게
 * 해석되고, 단위를 바꾸려면 문자열을 다시 뜯어야 한다.
 *
 * <p>단위 변환을 하지 않는다. kg으로 들어온 값을 g으로 바꿔 저장하면 관리자가
 * 적은 것과 저장된 것이 달라지고, 변환 규칙이 어디에 있는지 찾아야 한다.
 */
@Entity
@Table(name = "procurement_items")
public class ProcurementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private ProcurementRun run;

    /** 요청에 들어온 순서. 화면이 관리자가 적은 순서대로 보여 준다. */
    @Column(nullable = false)
    private int position;

    @Column(name = "ingredient_name", nullable = false, length = 100)
    private String ingredientName;

    @Column(nullable = false, length = 20)
    private String unit;

    @Column(name = "planned_quantity", nullable = false)
    private double plannedQuantity;

    @Column(name = "recommended_quantity", nullable = false)
    private double recommendedQuantity;

    /** 조정률. 모델이 주지 않으면 null이다. 0으로 채우지 않는다. */
    @Column(name = "adjustment_rate")
    private Double adjustmentRate;

    @Column(length = 500)
    private String reason;

    protected ProcurementItem() {
        // JPA 전용
    }

    ProcurementItem(
            ProcurementRun run,
            int position,
            String ingredientName,
            String unit,
            double plannedQuantity,
            double recommendedQuantity,
            Double adjustmentRate,
            String reason) {
        this.run = run;
        this.position = position;
        this.ingredientName = ingredientName;
        this.unit = unit;
        this.plannedQuantity = plannedQuantity;
        this.recommendedQuantity = recommendedQuantity;
        this.adjustmentRate = adjustmentRate;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public int getPosition() {
        return position;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public String getUnit() {
        return unit;
    }

    public double getPlannedQuantity() {
        return plannedQuantity;
    }

    public double getRecommendedQuantity() {
        return recommendedQuantity;
    }

    public Double getAdjustmentRate() {
        return adjustmentRate;
    }

    public String getReason() {
        return reason;
    }
}
