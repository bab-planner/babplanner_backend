package com.babplanner.backend.allergy.service;

import com.babplanner.backend.allergy.domain.Allergy;
import com.babplanner.backend.allergy.domain.UserAllergy;
import com.babplanner.backend.allergy.dto.AllergyResponse;
import com.babplanner.backend.allergy.repository.AllergyRepository;
import com.babplanner.backend.allergy.repository.UserAllergyRepository;
import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 알레르기 master 조회와 사용자별 설정. */
@Service
public class AllergyService {

    private final AllergyRepository allergyRepository;
    private final UserAllergyRepository userAllergyRepository;
    private final Clock clock;

    public AllergyService(
            AllergyRepository allergyRepository,
            UserAllergyRepository userAllergyRepository,
            Clock clock) {
        this.allergyRepository = allergyRepository;
        this.userAllergyRepository = userAllergyRepository;
        this.clock = clock;
    }

    /** 공개 master catalog. 정렬은 sortOrder 기준으로 항상 같다. */
    @Transactional(readOnly = true)
    public List<AllergyResponse> getCatalog() {
        return allergyRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(AllergyResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AllergyResponse> getMyAllergies(Long userId) {
        List<Long> ids = userAllergyRepository.findByUserId(userId).stream()
                .map(UserAllergy::getAllergyId)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return allergyRepository.findByIdInOrderBySortOrderAscIdAsc(ids).stream()
                .map(AllergyResponse::from)
                .toList();
    }

    /**
     * 전체 교체. 빈 배열이면 모두 해제한다.
     *
     * <p>존재하지 않는 id가 하나라도 있으면 조용히 무시하지 않고 실패시킨다.
     * 중복 id는 저장 전에 한 번으로 정규화한다.
     */
    @Transactional
    public List<AllergyResponse> updateMyAllergies(Long userId, List<Long> requestedIds) {
        Set<Long> uniqueIds = new LinkedHashSet<>();
        for (Long id : requestedIds) {
            if (id == null) {
                throw new ApiException(ErrorCode.UNKNOWN_ALLERGY);
            }
            uniqueIds.add(id);
        }

        List<Allergy> found =
                uniqueIds.isEmpty()
                        ? List.of()
                        : allergyRepository.findByIdInOrderBySortOrderAscIdAsc(uniqueIds);

        if (found.size() != uniqueIds.size()) {
            Set<Long> knownIds = new LinkedHashSet<>();
            for (Allergy allergy : found) {
                knownIds.add(allergy.getId());
            }
            Set<Long> unknown = new TreeSet<>(uniqueIds);
            unknown.removeAll(knownIds);
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("unknownAllergyIds", unknown);
            throw new ApiException(ErrorCode.UNKNOWN_ALLERGY, details);
        }

        Instant now = clock.instant();
        userAllergyRepository.deleteByUserId(userId);
        // 삭제와 삽입이 같은 트랜잭션에서 순서대로 반영되도록 먼저 비운다.
        userAllergyRepository.flush();
        for (Long allergyId : uniqueIds) {
            userAllergyRepository.save(new UserAllergy(userId, allergyId, now));
        }

        return found.stream().map(AllergyResponse::from).toList();
    }
}
