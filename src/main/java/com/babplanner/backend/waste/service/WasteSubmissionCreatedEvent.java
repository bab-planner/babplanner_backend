package com.babplanner.backend.waste.service;

/**
 * 제출이 저장된 뒤 발행되는 이벤트.
 *
 * <p>id만 담는다. 리스너는 커밋 이후에 자기 트랜잭션으로 다시 읽는다.
 */
public record WasteSubmissionCreatedEvent(Long submissionId) {}
