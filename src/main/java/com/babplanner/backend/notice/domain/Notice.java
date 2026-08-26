package com.babplanner.backend.notice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 공지사항.
 *
 * <p>본문은 plain text 계약이다. 줄바꿈은 그대로 보존하며 서버가 HTML이나
 * Markdown으로 해석하지 않는다.
 */
@Entity
@Table(name = "notices")
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(nullable = false)
    private boolean important;

    /**
     * 사용자에게 보이는지.
     *
     * <p>작성 중인 공지가 바로 나가지 않게 한다. 공개 목록과 상세는 이 값이
     * true인 것만 다룬다.
     */
    @Column(nullable = false)
    private boolean published;

    /**
     * 게시 시각.
     *
     * <p>공개 목록의 정렬 기준이다. 공개 여부와는 다른 값이며, published를
     * 껐다 켜도 이 값이 바뀌지 않는다. 원래 언제 쓴 글인지가 유지돼야 한다.
     */
    @Column(name = "published_at", nullable = false)
    private Instant publishedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Notice() {
        // JPA 전용
    }

    public Notice(
            String title,
            String content,
            boolean important,
            boolean published,
            Instant publishedAt,
            Instant now) {
        this.title = title;
        this.content = content;
        this.important = important;
        this.published = published;
        this.publishedAt = publishedAt;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 전체 내용을 새 값으로 바꾼다.
     *
     * <p>PUT은 부분 수정이 아니라 전체 교체다. publishedAt은 바꾸지 않는다.
     * 제목을 고쳤다고 글을 쓴 시각이 오늘로 바뀌면 목록 순서가 흔들린다.
     */
    public void applyWrite(
            String title, String content, boolean important, boolean published, Instant now) {
        this.title = title;
        this.content = content;
        this.important = important;
        this.published = published;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getContent() {
        return content;
    }

    public boolean isImportant() {
        return important;
    }

    public boolean isPublished() {
        return published;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
