package com.babplanner.backend.congestion.domain;

import com.babplanner.backend.site.domain.DiningSite;
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
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * 사이트별 현재 혼잡 상태.
 *
 * <p>사이트당 1행을 유지한다. 앱은 이 값을 그대로 표시하며, 인원수로 단계를
 * 계산하는 규칙은 어디에도 두지 않는다. 단계 판정은 향후 수집 파이프라인의
 * 책임이다.
 */
@Entity
@Table(name = "congestion_status")
public class CongestionStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private DiningSite site;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CongestionLevel level;

    @Column(name = "people_count")
    private Integer peopleCount;

    /** 서버가 정하는 표시 문구. 없을 수 있고, 앱이 만들어 내지 않는다. */
    @Column(name = "display_message", length = 100)
    private String displayMessage;

    @Column(length = 30)
    private String source;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CongestionStatus() {
        // JPA 전용
    }

    public Long getId() {
        return id;
    }

    public DiningSite getSite() {
        return site;
    }

    public CongestionLevel getLevel() {
        return level;
    }

    public Integer getPeopleCount() {
        return peopleCount;
    }

    public String getDisplayMessage() {
        return displayMessage;
    }

    public String getSource() {
        return source;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
