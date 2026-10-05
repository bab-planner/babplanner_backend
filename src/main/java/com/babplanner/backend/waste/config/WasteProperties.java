package com.babplanner.backend.waste.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 잔반 기능 설정. */
@ConfigurationProperties(prefix = "bapplanner.waste")
public class WasteProperties {

    /** 이미지 저장소 구현 선택. 기본값을 두지 않아 운영에서 로컬 디스크가 자동 선택되지 않는다. */
    private String storageMode;

    /** 로컬 저장소 루트. storage-mode=local일 때만 쓴다. */
    private String localRoot;

    /** 허용 이미지 최대 크기(byte). */
    private long maxImageBytes = 10L * 1024 * 1024;

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

    public long getMaxImageBytes() {
        return maxImageBytes;
    }

    public void setMaxImageBytes(long maxImageBytes) {
        this.maxImageBytes = maxImageBytes;
    }
}
