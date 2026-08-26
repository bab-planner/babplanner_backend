package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.waste.analysis.HttpWasteAnalyzer;
import com.babplanner.backend.waste.analysis.WasteAnalysisException;
import com.babplanner.backend.waste.analysis.WasteAnalysisInput;
import com.babplanner.backend.waste.storage.LocalWasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * 실제 연결이 있어야만 확인되는 두 가지.
 *
 * <p>연결 실패와 응답 지연은 흉내로는 확인되지 않는다. 상대는 이 테스트가
 * 띄운 loopback 서버이며 외부 네트워크로 나가지 않는다.
 */
class WasteAnalyzerSocketTest {

    private static final byte[] IMAGE_BYTES = "fake-image".getBytes(StandardCharsets.UTF_8);
    private static final String TOKEN = "test-only-service-token-not-a-real-secret";

    @TempDir Path storageRoot;

    private static HttpServer server;
    private static ExecutorService serverExecutor;

    private WasteImageStorage storage;
    private String storageKey;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", WasteAnalyzerSocketTest::handleSlowly);
        serverExecutor = Executors.newCachedThreadPool();
        server.setExecutor(serverExecutor);
        server.start();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop(0);
        }
        if (serverExecutor != null) {
            serverExecutor.shutdownNow();
        }
    }

    /** 클라이언트 읽기 제한보다 늦게 답한다. */
    private static void handleSlowly(HttpExchange exchange) throws IOException {
        try (InputStream stream = exchange.getRequestBody()) {
            stream.readAllBytes();
        }
        try {
            Thread.sleep(Duration.ofSeconds(3).toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        exchange.sendResponseHeaders(200, 0);
        exchange.close();
    }

    @BeforeEach
    void storeImage() {
        storage = new LocalWasteImageStorage(storageRoot);
        storageKey = storage
                .store(new WasteImageUpload(
                        () -> new ByteArrayInputStream(IMAGE_BYTES),
                        "a.jpg",
                        "image/jpeg",
                        IMAGE_BYTES.length))
                .storageKey();
    }

    private HttpWasteAnalyzer analyzer(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(500);
        return new HttpWasteAnalyzer(
                RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build(),
                storage,
                TOKEN);
    }

    @Test
    @DisplayName("연결하지 못하면 실패로 끝난다")
    void connectionFailureFails() {
        // 아무도 듣고 있지 않은 포트.
        HttpWasteAnalyzer offline = analyzer("http://127.0.0.1:1");

        assertThatThrownBy(() -> offline.analyze(new WasteAnalysisInput(7L, 3L, storageKey)))
                .isInstanceOf(WasteAnalysisException.class);
    }

    @Test
    @DisplayName("응답이 늦으면 실패로 끝난다")
    void readTimeoutFails() {
        HttpWasteAnalyzer slow = analyzer("http://127.0.0.1:" + server.getAddress().getPort());

        assertThatThrownBy(() -> slow.analyze(new WasteAnalysisInput(7L, 3L, storageKey)))
                .isInstanceOf(WasteAnalysisException.class);
    }
}
