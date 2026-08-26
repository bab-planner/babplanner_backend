package com.babplanner.backend.reward.dto;

import com.babplanner.backend.reward.domain.RewardTransaction;

/** OpenAPI RewardTransaction. type은 자유 문자열이다. */
public record RewardTransactionResponse(
        Long id, int amount, String type, String description, String createdAt) {

    public static RewardTransactionResponse from(RewardTransaction transaction) {
        return new RewardTransactionResponse(
                transaction.getId(),
                transaction.getAmount(),
                transaction.getType(),
                transaction.getDescription(),
                transaction.getCreatedAt().toString());
    }
}
