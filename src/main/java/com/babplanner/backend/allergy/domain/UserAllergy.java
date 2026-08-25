package com.babplanner.backend.allergy.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * 사용자가 선택한 알레르기 항목.
 *
 * <p>ManyToMany로 숨기지 않고 join 엔티티로 명시해 저장 시점을 남기고
 * 전체 교체(replacement) semantics를 정확히 통제한다.
 */
@Entity
@Table(
        name = "user_allergies",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uk_user_allergies_user_allergy",
                        columnNames = {"user_id", "allergy_id"}))
public class UserAllergy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "allergy_id", nullable = false)
    private Long allergyId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserAllergy() {
        // JPA 전용
    }

    public UserAllergy(Long userId, Long allergyId, Instant now) {
        this.userId = userId;
        this.allergyId = allergyId;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getAllergyId() {
        return allergyId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
