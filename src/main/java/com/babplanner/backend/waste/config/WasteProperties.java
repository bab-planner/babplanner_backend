package com.babplanner.backend.waste.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 잔반 기능 설정.
 *
 * <p>AI 서비스 주소와 토큰은 여기 없다. 잔반/수요/발주가 같은 FastAPI
 * 프로세스라서 {@code bapplanner.ai} 한 곳에서 정한다.
 */
@ConfigurationProperties(prefix = "bapplanner.waste")
public class WasteProperties {

    /** 이미지 저장소 구현 선택. 기본값을 두지 않아 운영에서 로컬 디스크가 자동 선택되지 않는다. */
    private String storageMode;

    /** 로컬 저장소 루트. storage-mode=local일 때만 쓴다. */
    private String localRoot;

    /** 분석기 구현 선택. 기본값 없음. fake 또는 http. */
    private String analyzerMode;

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

    public boolean isAsyncEnabled() {
        return asyncEnabled;
    }

    public void setAsyncEnabled(boolean asyncEnabled) {
        this.asyncEnabled = asyncEnabled;
    }
}
