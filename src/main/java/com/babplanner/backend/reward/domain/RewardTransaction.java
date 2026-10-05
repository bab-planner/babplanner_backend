package com.babplanner.backend.reward.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 포인트 변경 원장.
 *
 * <p>type은 OpenAPI에서 자유 문자열이다. enum으로 잠그면 서버가 새 종류를
 * 추가할 때 앱과 DB 양쪽을 바꿔야 하므로 문자열로 보관한다.
 */
@Entity
@Table(name = "reward_transactions")
public class RewardTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false)
    private int amount;

    @Column(nullable = false, length = 50)
    private String type;

    @Column(length = 200)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** 지급 출처 종류. 출처가 없는 거래는 null. (reference_type, reference_id)는 unique다. */
    @Column(name = "reference_type", length = 30)
    private String referenceType;

    @Column(name = "reference_id")
    private Long referenceId;

    protected RewardTransaction() {
        // JPA 전용
    }

    public RewardTransaction(
            Long userId, int amount, String type, String description, Instant now) {
        this.userId = userId;
        this.amount = amount;
        this.type = type;
        this.description = description;
        this.createdAt = now;
    }

    public RewardTransaction(
            Long userId,
            int amount,
            String type,
            String description,
            String referenceType,
            Long referenceId,
            Instant now) {
        this(userId, amount, type, description, now);
        this.referenceType = referenceType;
        this.referenceId = referenceId;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public Long getReferenceId() {
        return referenceId;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public int getAmount() {
        return amount;
    }

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
