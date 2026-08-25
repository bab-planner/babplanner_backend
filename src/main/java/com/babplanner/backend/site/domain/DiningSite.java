package com.babplanner.backend.site.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 식당(site).
 *
 * <p>Menu와 Congestion이 모두 참조하므로 식당 이름을 문자열로 흩뿌리지 않고
 * 하나의 엔티티로 둔다. OpenAPI에 Site 스키마가 없으므로 공개 API로 노출하지
 * 않고 내부 FK 소스로만 쓴다. {@code name}만 restaurantName으로 나간다.
 */
@Entity
@Table(name = "dining_sites")
public class DiningSite {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String name;

    /** 내부 식별 코드. API 응답에 노출하지 않는다. */
    @Column(nullable = false, length = 30)
    private String code;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected DiningSite() {
        // JPA 전용
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getCode() {
        return code;
    }

    public boolean isActive() {
        return active;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
