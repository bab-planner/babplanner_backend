package com.babplanner.backend.waste.storage;

/** 저장소 계층 실패. 파일시스템 예외를 그대로 위로 흘리지 않기 위한 경계다. */
public class WasteImageStorageException extends RuntimeException {

    public WasteImageStorageException(String message, Throwable cause) {
        super(message, cause);
    }

    public WasteImageStorageException(String message) {
        super(message);
    }
}
