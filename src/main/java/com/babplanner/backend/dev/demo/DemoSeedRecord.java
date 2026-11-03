package com.babplanner.backend.dev.demo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** 시연 데이터 시더가 만든 행의 소유권 기록. 이 기록이 있는 행만 정리 대상이다. */
@Entity
@Table(name = "demo_seed_records")
public class DemoSeedRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "entity_type", nullable = false, length = 40)
    private String entityType;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DemoSeedRecord() {
        // JPA 전용
    }

    public DemoSeedRecord(String entityType, Long entityId, Instant createdAt) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public Long getEntityId() {
        return entityId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
