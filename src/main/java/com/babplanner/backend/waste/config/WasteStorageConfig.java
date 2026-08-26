package com.babplanner.backend.waste.config;

import com.babplanner.backend.waste.storage.LocalWasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import java.nio.file.Path;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 이미지 저장소 구현 선택.
 *
 * <p>LocalWasteImageStorage는 서버 로컬 디스크에 쓴다. 여러 인스턴스로 뜨는
 * 운영 환경에서는 한 인스턴스가 저장한 이미지를 다른 인스턴스가 읽지 못하므로
 * 자동으로 선택되면 안 된다. 그래서 {@code bapplanner.waste.storage-mode=local}
 * 을 명시했을 때만 등록하고, 아무 구현도 없으면 기동에 실패한다.
 */
@Configuration
public class WasteStorageConfig {

    @Bean
    @ConditionalOnProperty(prefix = "bapplanner.waste", name = "storage-mode", havingValue = "local")
    public WasteImageStorage localWasteImageStorage(WasteProperties properties) {
        String root = properties.getLocalRoot();
        if (root == null || root.isBlank()) {
            throw new IllegalStateException(
                    "bapplanner.waste.local-root 가 필요합니다. storage-mode=local 을 쓰려면 "
                            + "이미지를 저장할 디렉터리를 지정하세요.");
        }
        return new LocalWasteImageStorage(Path.of(root));
    }

    /** 어떤 저장소도 구성되지 않은 경우. 조용히 넘어가지 않는다. */
    @Bean
    @ConditionalOnMissingBean(WasteImageStorage.class)
    public WasteImageStorage unconfiguredWasteImageStorage() {
        throw new IllegalStateException(
                "WasteImageStorage가 구성되지 않았습니다. 운영에서는 object storage 어댑터를 "
                        + "등록하고, 로컬/개발에서는 bapplanner.waste.storage-mode=local 을 명시하세요.");
    }
}
