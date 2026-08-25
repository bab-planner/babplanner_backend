package com.babplanner.backend.allergy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 알레르기 master 항목.
 *
 * <p>여기의 id가 서비스 전체의 source of truth다. 앱은 이 id를 받아
 * PUT /users/me/allergies 에 그대로 돌려준다.
 */
@Entity
@Table(name = "allergies")
public class Allergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    /** 노출 순서. API 응답 필드는 아니고 정렬에만 쓴다. */
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected Allergy() {
        // JPA 전용
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
