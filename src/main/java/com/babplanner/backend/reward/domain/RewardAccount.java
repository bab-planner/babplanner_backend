package com.babplanner.backend.reward.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자별 포인트 잔액.
 *
 * <p>잔액의 단일 source다. 원장(RewardTransaction)과 이 값을 서로 다른 곳에서
 * 따로 고치지 않는다. 향후 포인트 변동은 잔액 변경과 원장 생성을 한 트랜잭션
 * 안에서 함께 처리하는 서비스가 담당한다.
 *
 * <p>이번 단계에는 임의로 포인트를 바꾸는 공개 API가 없다.
 */
@Entity
@Table(name = "reward_accounts")
public class RewardAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int points;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected RewardAccount() {
        // JPA 전용
    }

    public RewardAccount(Long userId, int points, Instant now) {
        this.userId = userId;
        this.points = points;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getPoints() {
        return points;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
