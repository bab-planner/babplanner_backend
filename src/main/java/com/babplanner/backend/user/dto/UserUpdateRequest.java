package com.babplanner.backend.user.dto;

import com.babplanner.backend.user.domain.Gender;
import jakarta.validation.constraints.Size;

/**
 * OpenAPI UserUpdateRequest.
 *
 * <p>계약상 수정 가능한 필드는 name과 gender뿐이다. phone/studentId/role/id/
 * password는 DTO에 아예 두지 않아 요청으로 들어와도 무시된다.
 */
public record UserUpdateRequest(@Size(max = 50) String name, Gender gender) {}
