package com.babplanner.backend.waste.storage;

import java.io.InputStream;

/**
 * 잔반 이미지 저장 경계.
 *
 * <p>도메인/서비스는 파일시스템도 object storage도 알지 못한다. 향후
 * S3 어댑터로 갈아끼울 수 있도록 이 인터페이스 뒤에만 구현을 둔다.
 */
public interface WasteImageStorage {

    /** 저장하고 서버가 생성한 키를 돌려준다. 사용자 파일 이름을 키로 쓰지 않는다. */
    StoredWasteImage store(WasteImageUpload image);

    boolean exists(String storageKey);

    /** 분석 시 이미지를 읽는다. 키가 없으면 예외를 던진다. */
    InputStream read(String storageKey);

    /** best-effort 삭제. 실패해도 예외를 밖으로 던지지 않는다. */
    void delete(String storageKey);
}
