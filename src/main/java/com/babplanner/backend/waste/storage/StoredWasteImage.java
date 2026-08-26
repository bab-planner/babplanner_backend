package com.babplanner.backend.waste.storage;

/**
 * 저장소가 발급한 이미지 식별자.
 *
 * <p>서버의 실제 파일 경로를 담지 않는다. 도메인/서비스는 storageKey만 알고,
 * 그 키가 어디에 어떻게 저장됐는지는 저장소 구현만 안다.
 */
public record StoredWasteImage(String storageKey, String contentType, long size) {}
