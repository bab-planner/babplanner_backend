package com.babplanner.backend.user.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.user.domain.UserEntity;
import com.babplanner.backend.user.dto.UserResponse;
import com.babplanner.backend.user.dto.UserUpdateRequest;
import com.babplanner.backend.user.repository.UserRepository;
import java.time.Clock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 내 회원정보 조회/수정. 항상 인증된 principal의 id로만 동작한다. */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final Clock clock;

    public UserService(UserRepository userRepository, Clock clock) {
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public UserResponse getMe(Long userId) {
        return UserResponse.from(findUser(userId));
    }

    @Transactional
    public UserResponse updateMe(Long userId, UserUpdateRequest request) {
        UserEntity user = findUser(userId);
        // name과 gender만 반영한다. 휴대폰/학번/권한은 요청 DTO에 존재하지도 않는다.
        user.updateProfile(request.name(), request.gender(), clock.instant());
        return UserResponse.from(user);
    }

    private UserEntity findUser(Long userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND));
    }
}
