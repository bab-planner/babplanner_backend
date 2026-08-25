package com.babplanner.backend.common.security;

import com.babplanner.backend.user.domain.Role;

/**
 * 인증된 사용자 principal.
 *
 * <p>Controller는 request body나 query parameter가 아니라 이 principal에서만
 * 사용자 id를 얻는다.
 */
public record AuthenticatedUser(Long id, Role role) {}
