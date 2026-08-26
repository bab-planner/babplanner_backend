package com.babplanner.backend.waste.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * 저장할 이미지 한 건.
 *
 * <p>Spring의 MultipartFile을 저장소 구현 밖으로 흘리지 않기 위한 표현이다.
 * originalFilename은 진단용일 뿐이며 파일 이름으로 그대로 쓰지 않는다.
 */
public record WasteImageUpload(
        InputStreamSupplier content, String originalFilename, String contentType, long size) {

    @FunctionalInterface
    public interface InputStreamSupplier {
        InputStream open() throws IOException;
    }
}
