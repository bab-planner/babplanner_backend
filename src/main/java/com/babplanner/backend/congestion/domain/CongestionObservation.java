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
 * 한 시점의 혼잡도 관측값.
 *
 * <p>{@link CongestionStatus}는 사이트마다 한 행만 두고 덮어쓰기 때문에 지난
 * 값이 남지 않는다. 시계열은 이 테이블만 본다.
 *
 * <p>기록된 사실이므로 한 번 저장한 뒤에는 고치지 않는다. setter를 두지 않은
 * 것도 그래서다.
 */
@Entity
@Table(name = "congestion_observations")
public class CongestionObservation {

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

    @Column(name = "display_message", length = 100)
    private String displayMessage;

    @Column(length = 30)
    private String source;

    /**
     * 관측 장비 식별자.
     *
     * <p>사람이 아니라 장비를 가리킨다. 어느 카메라가 이상한 값을 보내는지
     * 추적하려면 필요하다. 사용자 식별 정보는 여기 들어오지 않는다.
     *
     * <p>V22가 옮겨 둔 기존 행에는 값이 없어 nullable이다.
     */
    @Column(name = "device_id", length = 100)
    private String deviceId;

    /** 판정에 쓴 모델 버전. 값이 튀기 시작한 시점을 찾는 데 쓴다. */
    @Column(name = "model_version", length = 100)
    private String modelVersion;

    @Column(name = "observed_at", nullable = false)
    private Instant observedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected CongestionObservation() {}

    public CongestionObservation(
            DiningSite site,
            CongestionLevel level,
            Integer peopleCount,
            String displayMessage,
            String source,
            String deviceId,
            String modelVersion,
            Instant observedAt,
            Instant createdAt) {
        this.site = site;
        this.level = level;
        this.peopleCount = peopleCount;
        this.displayMessage = displayMessage;
        this.source = source;
        this.deviceId = deviceId;
        this.modelVersion = modelVersion;
        this.observedAt = observedAt;
        this.createdAt = createdAt;
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

    public String getDeviceId() {
        return deviceId;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public Instant getObservedAt() {
        return observedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
