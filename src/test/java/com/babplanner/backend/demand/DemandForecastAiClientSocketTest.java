package com.babplanner.backend.demand;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.babplanner.backend.common.client.AiRestClientProvider;
import com.babplanner.backend.common.client.AiRestClients;
import com.babplanner.backend.common.client.AiServiceException;
import com.babplanner.backend.common.config.AiServiceProperties;
import com.babplanner.backend.demand.client.DemandForecastAiClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실제 연결이 있어야만 확인되는 두 가지: 응답 지연과 연결 실패.
 *
 * <p>운영과 같은 {@link AiRestClients}로 클라이언트를 만든다. 시간 제한이 실제로
 * 적용되는지를 보려는 것이다. 상대는 이 테스트가 띄운 loopback 서버이며 외부
 * 네트워크로 나가지 않는다.
 */
class DemandForecastAiClientSocketTest {

    private static HttpServer server;
    private static ExecutorService serverExecutor;

    @BeforeAll
    static void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", DemandForecastAiClientSocketTest::respondTooLate);
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

    /** 클라이언트 읽기 제한보다 늦게, 그러나 정상처럼 보이는 응답을 준다. */
    private static void respondTooLate(HttpExchange exchange) throws IOException {
        try (InputStream stream = exchange.getRequestBody()) {
            stream.readAllBytes();
        }
        try {
            Thread.sleep(Duration.ofSeconds(3).toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        byte[] body = DemandForecastAiClientTest.SUCCESS_BODY.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    /** 운영 설정 객체와 운영 팩토리로 만든 클라이언트. 시간 제한만 짧게 준다. */
    private static DemandForecastAiClient client(String baseUrl) {
        AiServiceProperties properties = new AiServiceProperties();
        properties.setBaseUrl(baseUrl);
        properties.setServiceToken(DemandForecastAiClientTest.TOKEN);
        properties.setConnectTimeout(Duration.ofMillis(1000));
        properties.setReadTimeout(Duration.ofMillis(500));
        return new DemandForecastAiClient(new AiRestClientProvider(properties));
    }

    @Test
    @DisplayName("응답이 읽기 제한보다 늦으면 예측값 없이 실패한다")
    void readTimeoutFails() {
        DemandForecastAiClient slow = client("http://127.0.0.1:" + server.getAddress().getPort());
        long started = System.nanoTime();

        // 늦게라도 정상 응답이 오지만, 기다리지 않고 실패로 끝낸다.
        assertThatThrownBy(() -> slow.predict(DemandForecastAiClientTest.request()))
                .isInstanceOf(AiServiceException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(3));
    }

    @Test
    @DisplayName("status도 읽기 제한을 지킨다")
    void statusReadTimeoutFails() {
        DemandForecastAiClient slow = client("http://127.0.0.1:" + server.getAddress().getPort());

        assertThatThrownBy(slow::status).isInstanceOf(AiServiceException.class);
    }

    @Test
    @DisplayName("연결하지 못하면 실패한다")
    void connectionFailureFails() {
        // 아무도 듣고 있지 않은 포트.
        DemandForecastAiClient offline = client("http://127.0.0.1:1");

        assertThatThrownBy(() -> offline.predict(DemandForecastAiClientTest.request()))
                .isInstanceOf(AiServiceException.class);
    }
}
