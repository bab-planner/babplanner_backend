package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.waste.storage.LocalWasteImageStorage;
import com.babplanner.backend.waste.storage.StoredWasteImage;
import com.babplanner.backend.waste.storage.WasteImageStorageException;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalWasteImageStorageTest {

    @TempDir Path root;

    private static final byte[] CONTENT = "fake-image-bytes".getBytes(StandardCharsets.UTF_8);

    private WasteImageUpload upload(String originalFilename) {
        return upload(originalFilename, CONTENT);
    }

    private WasteImageUpload upload(String originalFilename, byte[] content) {
        return new WasteImageUpload(
                () -> new ByteArrayInputStream(content),
                originalFilename,
                "image/jpeg",
                content.length);
    }

    private LocalWasteImageStorage storage() {
        return new LocalWasteImageStorage(root);
    }

    @Test
    @DisplayName("저장하면 서버가 만든 키를 돌려준다")
    void storeReturnsGeneratedKey() {
        StoredWasteImage stored = storage().store(upload("점심.jpg"));

        assertThat(stored.storageKey()).isNotBlank();
        assertThat(stored.contentType()).isEqualTo("image/jpeg");
        assertThat(stored.size()).isEqualTo(CONTENT.length);
    }

    @Test
    @DisplayName("업로드한 파일 이름이 키나 파일 이름에 쓰이지 않는다")
    void originalFilenameIsNotUsed() throws IOException {
        StoredWasteImage stored = storage().store(upload("점심.jpg"));

        assertThat(stored.storageKey()).doesNotContain("점심").doesNotContain(".jpg");
        try (Stream<Path> files = Files.list(root)) {
            assertThat(files.map(path -> path.getFileName().toString()))
                    .containsExactly(stored.storageKey());
        }
    }

    @Test
    @DisplayName("키는 매번 다르다")
    void keysAreUnique() {
        LocalWasteImageStorage storage = storage();

        String first = storage.store(upload("a.jpg")).storageKey();
        String second = storage.store(upload("a.jpg")).storageKey();

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("저장한 내용을 그대로 읽는다")
    void readsBackWhatWasStored() throws IOException {
        LocalWasteImageStorage storage = storage();
        String key = storage.store(upload("a.jpg")).storageKey();

        try (InputStream stream = storage.read(key)) {
            assertThat(stream.readAllBytes()).isEqualTo(CONTENT);
        }
    }

    @Test
    @DisplayName("존재 여부를 알려준다")
    void reportsExistence() {
        LocalWasteImageStorage storage = storage();
        String key = storage.store(upload("a.jpg")).storageKey();

        assertThat(storage.exists(key)).isTrue();
        assertThat(storage.exists("00000000000000000000000000000000")).isFalse();
    }

    @Test
    @DisplayName("삭제하면 더 이상 존재하지 않는다")
    void deleteRemovesFile() {
        LocalWasteImageStorage storage = storage();
        String key = storage.store(upload("a.jpg")).storageKey();

        storage.delete(key);

        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    @DisplayName("없는 것을 지워도 예외를 던지지 않는다")
    void deleteIsBestEffort() {
        assertThat(root).exists();

        storage().delete("00000000000000000000000000000000");
    }

    @Test
    @DisplayName("루트 밖으로 나가는 키는 거부한다")
    void rejectsPathTraversal() {
        LocalWasteImageStorage storage = storage();

        for (String key : new String[] {
            "../escaped", "../../escaped", "sub/../../escaped", "..", "./.."
        }) {
            assertThatThrownBy(() -> storage.exists(key))
                    .as("%s", key)
                    .isInstanceOf(WasteImageStorageException.class);
        }
    }

    @Test
    @DisplayName("빈 키는 거부한다")
    void rejectsBlankKey() {
        LocalWasteImageStorage storage = storage();

        assertThatThrownBy(() -> storage.exists("  "))
                .isInstanceOf(WasteImageStorageException.class);
        assertThatThrownBy(() -> storage.exists(null))
                .isInstanceOf(WasteImageStorageException.class);
    }

    @Test
    @DisplayName("읽을 수 없는 입력이면 파일을 남기지 않는다")
    void failedStoreLeavesNothingBehind() throws IOException {
        LocalWasteImageStorage storage = storage();
        WasteImageUpload broken = new WasteImageUpload(
                () -> {
                    throw new IOException("읽기 실패");
                },
                "a.jpg",
                "image/jpeg",
                10);

        assertThatThrownBy(() -> storage.store(broken))
                .isInstanceOf(WasteImageStorageException.class);

        try (Stream<Path> files = Files.list(root)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    @DisplayName("루트 디렉터리가 없으면 만든다")
    void createsRootIfMissing() {
        Path nested = root.resolve("a/b/c");

        LocalWasteImageStorage storage = new LocalWasteImageStorage(nested);

        assertThat(nested).exists();
        assertThat(storage.store(upload("a.jpg")).storageKey()).isNotBlank();
    }

    @Test
    @DisplayName("없는 이미지를 읽으면 예외를 던진다")
    void readingMissingImageThrows() {
        LocalWasteImageStorage storage = storage();

        assertThatThrownBy(() -> storage.read("00000000000000000000000000000000"))
                .isInstanceOf(WasteImageStorageException.class);
    }
}
