package com.babplanner.backend.internal.controller;

import com.babplanner.backend.congestion.dto.CongestionObservationRequest;
import com.babplanner.backend.congestion.service.CongestionIngestService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 혼잡도 관측 수집.
 *
 * <p>사용자 앱은 이 경로를 부르지 않는다. 접근 권한은 사용자 JWT가 아니라
 * 서비스 토큰이 정하며, 규칙은 SecurityConfig 한 곳에 있다.
 *
 * <p>본문을 돌려주지 않는다. OpenAPI가 202로 정의하고 있고, 수집기는 저장된
 * 값을 다시 읽을 이유가 없다.
 */
@RestController
@RequestMapping("/internal/v1/congestion-observations")
public class InternalCongestionController {

    private final CongestionIngestService congestionIngestService;

    public InternalCongestionController(CongestionIngestService congestionIngestService) {
        this.congestionIngestService = congestionIngestService;
    }

    @PostMapping
    public ResponseEntity<Void> ingest(@Valid @RequestBody CongestionObservationRequest request) {
        congestionIngestService.ingest(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).build();
    }
}
