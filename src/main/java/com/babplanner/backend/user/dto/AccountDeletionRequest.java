package com.babplanner.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 회원 탈퇴 요청. 본인 확인용 현재 비밀번호만 받는다. */
public record AccountDeletionRequest(@NotBlank @Size(max = 200) String password) {}
