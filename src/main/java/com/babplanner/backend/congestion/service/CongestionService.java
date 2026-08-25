package com.babplanner.backend.congestion.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.congestion.dto.CongestionResponse;
import com.babplanner.backend.congestion.repository.CongestionStatusRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 현재 혼잡도 조회. 저장된 상태를 그대로 돌려주기만 한다. */
@Service
public class CongestionService {

    private final CongestionStatusRepository repository;

    public CongestionService(CongestionStatusRepository repository) {
        this.repository = repository;
    }

    /**
     * 현재 혼잡도.
     *
     * <p>siteId를 주면 해당 사이트, 없으면 노출 순서가 가장 앞선 대표 사이트를
     * 본다. 상태가 없으면 404다. 임의의 기본 단계를 지어내지 않는다.
     */
    @Transactional(readOnly = true)
    public CongestionResponse getCurrent(Long siteId) {
        return (siteId == null
                        ? repository.findFirstByOrderBySiteSortOrderAscSiteIdAsc()
                        : repository.findBySiteId(siteId))
                .map(CongestionResponse::from)
                .orElseThrow(() -> new ApiException(ErrorCode.CONGESTION_NOT_AVAILABLE));
    }
}
