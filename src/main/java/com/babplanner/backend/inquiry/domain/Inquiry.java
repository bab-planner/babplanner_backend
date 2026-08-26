package com.babplanner.backend.inquiry.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사용자가 남긴 문의/건의.
 *
 * <p>관리자 답변 필드를 미리 두되, 이번 단계에서 답변을 쓰는 API는 만들지
 * 않는다. 조회는 항상 본인 것만 가능하다.
 */
@Entity
@Table(name = "inquiries")
public class Inquiry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryCategory category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InquiryStatus status;

    @Column(name = "admin_answer", columnDefinition = "TEXT")
    private String adminAnswer;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Inquiry() {
        // JPA 전용
    }

    public Inquiry(
            Long userId, InquiryCategory category, String title, String content, Instant now) {
        this.userId = userId;
        this.category = category;
        this.title = title;
        this.content = content;
        this.status = InquiryStatus.RECEIVED;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /** 상태를 바꾼다. 요청이 보냈을 때만 부른다. */
    public void changeStatus(InquiryStatus status, Instant now) {
        this.status = status;
        this.updatedAt = now;
    }

    /**
     * 답변을 쓰거나 지운다.
     *
     * <p>답변을 썼다고 상태를 자동으로 ANSWERED로 바꾸지 않는다. 답변을 적어
     * 두고 아직 검토 중일 수 있고, 상태는 관리자가 명시적으로 정하는 값이다.
     */
    public void applyAnswer(String adminAnswer, Instant now) {
        this.adminAnswer = adminAnswer;
        // 답변이 없어지면 답변 시각도 없어진다. 남겨 두면 "답변했는데 내용이
        // 없다"는 상태가 된다.
        this.answeredAt = adminAnswer == null ? null : now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public InquiryCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public InquiryStatus getStatus() {
        return status;
    }

    public String getAdminAnswer() {
        return adminAnswer;
    }

    public Instant getAnsweredAt() {
        return answeredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
