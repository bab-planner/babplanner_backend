package com.babplanner.backend.waste.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 잔반 기능 설정. */
@ConfigurationProperties(prefix = "bapplanner.waste")
public class WasteProperties {

    /** 이미지 저장소 구현 선택. 기본값을 두지 않아 운영에서 로컬 디스크가 자동 선택되지 않는다. */
    private String storageMode;

    /** 로컬 저장소 루트. storage-mode=local일 때만 쓴다. */
    private String localRoot;

    /** 분석기 구현 선택. 기본값 없음. fake 또는 http. */
    private String analyzerMode;

    /** 분석 서비스 주소. analyzer-mode=http일 때만 쓴다. 기본값 없음. */
    private String aiBaseUrl;

    /** 분석 서비스 인증 토큰. analyzer-mode=http일 때만 쓴다. 기본값 없음. */
    private String aiServiceToken;

    /** 분석 서비스 연결 대기 시간. */
    private Duration aiConnectTimeout = Duration.ofSeconds(5);

    /** 분석 서비스 응답 대기 시간. 분석은 요청 스레드가 아니라 작업 스레드가 기다린다. */
    private Duration aiReadTimeout = Duration.ofSeconds(30);

    /** 허용 이미지 최대 크기(byte). */
    private long maxImageBytes = 10L * 1024 * 1024;

    /**
     * 분석을 별도 스레드에서 돌릴지 여부.
     *
     * <p>기본은 비동기다. 끄면 업로드 요청 스레드가 분석까지 마치므로 결과를
     * 곧바로 확인해야 하는 테스트에서만 쓴다.
     */
    private boolean asyncEnabled = true;

    public String getStorageMode() {
        return storageMode;
    }

    public void setStorageMode(String storageMode) {
        this.storageMode = storageMode;
    }

    public String getLocalRoot() {
        return localRoot;
    }

    public void setLocalRoot(String localRoot) {
        this.localRoot = localRoot;
    }

    public String getAnalyzerMode() {
        return analyzerMode;
    }

    public void setAnalyzerMode(String analyzerMode) {
        this.analyzerMode = analyzerMode;
    }

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }

    public String getAiBaseUrl() {
        return aiBaseUrl;
    }

    public void setAiBaseUrl(String aiBaseUrl) {
        this.aiBaseUrl = aiBaseUrl;
    }

    public String getAiServiceToken() {
        return aiServiceToken;
    }

    public void setAiServiceToken(String aiServiceToken) {
        this.aiServiceToken = aiServiceToken;
    }

    public Duration getAiConnectTimeout() {
        return aiConnectTimeout;
    }

    public void setAiConnectTimeout(Duration aiConnectTimeout) {
        this.aiConnectTimeout = aiConnectTimeout;
    }

    public Duration getAiReadTimeout() {
        return aiReadTimeout;
    }

    public void setAiReadTimeout(Duration aiReadTimeout) {
        this.aiReadTimeout = aiReadTimeout;
    }

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }
}
