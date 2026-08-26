package com.babplanner.backend.waste.analysis;

/**
 * 분석 입력.
 *
 * <p>이미지는 저장소 키로만 전달한다. 분석기가 필요하면 저장소를 통해 읽는다.
 * 서버 파일 경로를 넘기지 않는다.
 */
public record WasteAnalysisInput(long submissionId, long menuId, String imageStorageKey) {}
