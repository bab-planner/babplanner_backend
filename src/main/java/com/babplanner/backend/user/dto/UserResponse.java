package com.babplanner.backend.user.dto;

import com.babplanner.backend.user.domain.Gender;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;

/**
 * OpenAPI User.
 *
 * <p>passwordHash를 비롯한 내부 필드는 절대 포함하지 않는다.
 */
public record UserResponse(
        Long id, String name, String phone, String studentId, Gender gender, Role role) {

    public static UserResponse from(UserEntity user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getPhone(),
                user.getStudentId(),
                user.getGender(),
                user.getRole());
    }
}
