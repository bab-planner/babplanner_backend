package com.babplanner.backend.waste.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 로컬 디스크 저장소.
 *
 * <p>로컬/개발 환경 전용이다. 운영에서는 object storage 어댑터가 필요하며,
 * 이 구현이 자동으로 선택되지 않도록 WasteStorageConfig가 명시적 설정을
 * 요구한다.
 *
 * <p>키는 서버가 만든 UUID다. 사용자가 올린 파일 이름을 파일 이름으로 쓰면
 * 경로 조작이나 충돌이 생길 수 있어 쓰지 않는다.
 */
public class LocalWasteImageStorage implements WasteImageStorage {

    private static final Logger log = LoggerFactory.getLogger(LocalWasteImageStorage.class);

    private final Path root;

    public LocalWasteImageStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.root);
        } catch (IOException exception) {
            throw new WasteImageStorageException("이미지 저장 위치를 준비하지 못했습니다.", exception);
        }
    }

    @Override
    public StoredWasteImage store(WasteImageUpload image) {
        // 서버가 만든 키만 사용한다. 확장자도 붙이지 않아 실행 가능한 이름이 될 수 없다.
        String storageKey = UUID.randomUUID().toString().replace("-", "");
        Path target = resolve(storageKey);
        try (InputStream input = image.content().open()) {
            long size = Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
            return new StoredWasteImage(storageKey, image.contentType(), size);
        } catch (IOException exception) {
            // 부분 저장본을 남기지 않는다.
            deleteQuietly(target);
            throw new WasteImageStorageException("이미지를 저장하지 못했습니다.", exception);
        }
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    @Override
    public InputStream read(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException exception) {
            throw new WasteImageStorageException("이미지를 읽지 못했습니다.", exception);
        }
    }

    @Override
    public void delete(String storageKey) {
        deleteQuietly(resolve(storageKey));
    }

    /**
     * 키를 실제 경로로 바꾼다.
     *
     * <p>키가 경로 구분자나 상위 이동을 포함해 root 밖으로 나가지 못하게 막는다.
     */
    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new WasteImageStorageException("이미지 키가 비어 있습니다.");
        }
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root) || resolved.equals(root)) {
            throw new WasteImageStorageException("허용되지 않는 이미지 키입니다.");
        }
        return resolved;
    }

    private void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            // 삭제 실패가 원래 오류를 가리지 않도록 경로 없이 남긴다.
            log.warn("임시 이미지 파일을 정리하지 못했습니다.");
        }
    }
}
