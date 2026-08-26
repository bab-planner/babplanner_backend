package com.babplanner.backend.waste;

import static org.assertj.core.api.Assertions.assertThat;

import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.support.ApiIntegrationTest;
import com.babplanner.backend.waste.analysis.WasteAnalysisDecision;
import com.babplanner.backend.waste.analysis.WasteAnalysisInput;
import com.babplanner.backend.waste.analysis.WasteAnalysisResult;
import com.babplanner.backend.waste.analysis.WasteAnalyzer;
import com.babplanner.backend.waste.config.WasteAsyncConfig;
import com.babplanner.backend.waste.config.WasteProperties;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.service.WasteProcessingDispatcher;
import com.babplanner.backend.waste.service.WasteRecoveryRunner;
import com.babplanner.backend.waste.service.WasteSubmissionProcessor;
import com.babplanner.backend.waste.service.WasteSubmissionStateStore;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import com.fasterxml.jackson.databind.JsonNode;
import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 작업 전달 경계.
 *
 * <p>확인하려는 것은 하나다. 분석은 언제나 executor 작업 스레드에서만
 * 실행되고, 넘기지 못한 제출은 진행 중 상태로 방치되지 않는다.
 */
class WasteProcessingDispatcherTest extends ApiIntegrationTest {

    private static final byte[] IMAGE_BYTES = "fake-image".getBytes(StandardCharsets.UTF_8);

    @Autowired private WasteSubmissionStateStore stateStore;
    @Autowired private WasteImageStorage imageStorage;
    @Autowired private MenuRepository menuRepository;
    @Autowired private WasteRecoveryRunner recoveryRunner;

    private Long userId;

    /** 분석 호출 횟수와 실행 스레드를 기록한다. */
    private static final class RecordingAnalyzer implements WasteAnalyzer {

        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicReference<String> lastThread = new AtomicReference<>();
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch release;

        private RecordingAnalyzer(CountDownLatch release) {
            this.release = release;
        }

        @Override
        public WasteAnalysisResult analyze(WasteAnalysisInput input) {
            calls.incrementAndGet();
            lastThread.set(Thread.currentThread().getName());
            started.countDown();
            if (release != null) {
                try {
                    release.await(20, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
            return new WasteAnalysisResult(
                    WasteAnalysisDecision.APPROVED, 0.15, 0.92, "spring-fixture-waste-v1");
        }
    }

    private WasteProcessingDispatcher dispatcherWith(TaskExecutor executor, WasteAnalyzer analyzer) {
        return new WasteProcessingDispatcher(
                executor,
                new WasteSubmissionProcessor(stateStore, imageStorage, analyzer),
                stateStore);
    }

    private ThreadPoolTaskExecutor boundedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(1);
        executor.setThreadNamePrefix("test-waste-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }

    /** 같은 번호로 두 번 가입할 수 없으므로 테스트마다 한 번만 가입한다. */
    private long userId() throws Exception {
        if (userId == null) {
            JsonNode auth = signup();
            userId = auth.path("user").path("id").asLong();
        }
        return userId;
    }

    private long newSubmission() throws Exception {
        long owner = userId();
        String storageKey = imageStorage
                .store(new WasteImageUpload(
                        () -> new ByteArrayInputStream(IMAGE_BYTES),
                        "a.jpg",
                        "image/jpeg",
                        IMAGE_BYTES.length))
                .storageKey();
        Instant now = clock.instant();
        return wasteSubmissionRepository
                .save(new WasteSubmission(
                        owner,
                        menuRepository.findAll().getFirst().getId(),
                        storageKey,
                        now,
                        now))
                .getId();
    }

    private WasteSubmissionStatus statusOf(long id) {
        return wasteSubmissionRepository.findById(id).orElseThrow().getStatus();
    }

    @Test
    @DisplayName("거절되면 호출 스레드에서 분석하지 않는다")
    void rejectionNeverRunsOnCallingThread() throws Exception {
        long id = newSubmission();
        RecordingAnalyzer analyzer = new RecordingAnalyzer(null);
        // 무엇을 넘기든 거절하는 executor.
        TaskExecutor alwaysRejects = task -> {
            throw new RejectedExecutionException("queue full");
        };

        dispatcherWith(alwaysRejects, analyzer).dispatch(id);

        assertThat(analyzer.calls).hasValue(0);
    }

    @Test
    @DisplayName("거절된 제출은 RECEIVED로 남지 않고 FAILED로 끝난다")
    void rejectedSubmissionEndsAsFailed() throws Exception {
        long id = newSubmission();
        assertThat(statusOf(id)).isEqualTo(WasteSubmissionStatus.RECEIVED);
        TaskExecutor alwaysRejects = task -> {
            throw new RejectedExecutionException("queue full");
        };

        dispatcherWith(alwaysRejects, new RecordingAnalyzer(null)).dispatch(id);

        assertThat(statusOf(id)).isEqualTo(WasteSubmissionStatus.FAILED);
    }

    @Test
    @DisplayName("거절돼도 예외가 호출자에게 새지 않는다")
    void rejectionIsNotPropagated() throws Exception {
        long id = newSubmission();
        TaskExecutor alwaysRejects = task -> {
            throw new RejectedExecutionException("queue full");
        };

        dispatcherWith(alwaysRejects, new RecordingAnalyzer(null)).dispatch(id);

        // 업로드 요청이 500으로 끝나지 않아야 한다.
        assertThat(statusOf(id)).isEqualTo(WasteSubmissionStatus.FAILED);
    }

    @Test
    @DisplayName("거절돼도 리워드는 움직이지 않는다")
    void rejectionGrantsNoReward() throws Exception {
        long id = newSubmission();
        TaskExecutor alwaysRejects = task -> {
            throw new RejectedExecutionException("queue full");
        };

        dispatcherWith(alwaysRejects, new RecordingAnalyzer(null)).dispatch(id);

        WasteSubmission stored = wasteSubmissionRepository.findById(id).orElseThrow();
        assertThat(stored.getEarnedPoints()).isNull();
        assertThat(stored.getCouponIssued()).isNull();
        assertThat(rewardTransactionRepository.findAll()).isEmpty();
        assertThat(couponRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("정상 전달은 다른 스레드에서 분석한다")
    void normalDispatchRunsOnWorkerThread() throws Exception {
        long id = newSubmission();
        RecordingAnalyzer analyzer = new RecordingAnalyzer(null);
        ThreadPoolTaskExecutor executor = boundedExecutor();
        try {
            dispatcherWith(executor, analyzer).dispatch(id);

            assertThat(analyzer.started.await(20, TimeUnit.SECONDS)).isTrue();
        } finally {
            executor.shutdown();
        }

        assertThat(analyzer.lastThread.get()).startsWith("test-waste-");
        assertThat(analyzer.lastThread.get()).isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    @DisplayName("전달은 분석이 끝나기를 기다리지 않는다")
    void dispatchDoesNotWaitForAnalysis() throws Exception {
        long id = newSubmission();
        CountDownLatch release = new CountDownLatch(1);
        RecordingAnalyzer analyzer = new RecordingAnalyzer(release);
        ThreadPoolTaskExecutor executor = boundedExecutor();
        try {
            dispatcherWith(executor, analyzer).dispatch(id);
            // 분석이 아직 붙잡혀 있는 동안에도 전달은 이미 반환됐다.
            assertThat(analyzer.started.await(20, TimeUnit.SECONDS)).isTrue();
            assertThat(statusOf(id)).isNotIn(
                    WasteSubmissionStatus.APPROVED,
                    WasteSubmissionStatus.REJECTED,
                    WasteSubmissionStatus.FAILED);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("큐가 포화되면 넘친 건만 FAILED가 되고 호출 스레드는 분석하지 않는다")
    void saturationFailsOverflowOnly() throws Exception {
        long busy = newSubmission();
        long queued = newSubmission();
        long overflow = newSubmission();
        CountDownLatch release = new CountDownLatch(1);
        RecordingAnalyzer analyzer = new RecordingAnalyzer(release);
        ThreadPoolTaskExecutor executor = boundedExecutor();
        String callerThread = Thread.currentThread().getName();
        try {
            WasteProcessingDispatcher dispatcher = dispatcherWith(executor, analyzer);
            dispatcher.dispatch(busy);
            // 첫 건이 worker를 잡을 때까지 기다린 뒤에야 큐가 실제로 찬다.
            assertThat(analyzer.started.await(20, TimeUnit.SECONDS)).isTrue();
            dispatcher.dispatch(queued);
            dispatcher.dispatch(overflow);

            assertThat(statusOf(overflow)).isEqualTo(WasteSubmissionStatus.FAILED);
            assertThat(analyzer.lastThread.get()).isNotEqualTo(callerThread);
        } finally {
            release.countDown();
            executor.shutdown();
        }
    }

    @Test
    @DisplayName("운영 executor는 거절만 하고 호출 스레드로 되돌리지 않는다")
    void productionExecutorAborts() {
        WasteProperties properties = new WasteProperties();
        properties.setAsyncEnabled(true);

        Executor executor = new WasteAsyncConfig().wasteAnalysisExecutor(properties);

        assertThat(executor).isInstanceOf(ThreadPoolTaskExecutor.class);
        ThreadPoolTaskExecutor pool = (ThreadPoolTaskExecutor) executor;
        try {
            assertThat(pool.getThreadPoolExecutor().getRejectedExecutionHandler())
                    .isInstanceOf(ThreadPoolExecutor.AbortPolicy.class);
            assertThat(pool.getThreadPoolExecutor().getQueue().remainingCapacity())
                    .as("큐가 무한하면 포화 정책 자체가 의미가 없다")
                    .isLessThan(Integer.MAX_VALUE);
        } finally {
            pool.shutdown();
        }
    }

    @Test
    @DisplayName("복구도 같은 dispatcher를 쓴다")
    void recoveryUsesTheSameDispatcher() {
        List<Class<?>> fieldTypes = Arrays.stream(WasteRecoveryRunner.class.getDeclaredFields())
                .map(Field::getType)
                .toList();

        assertThat(fieldTypes).contains(WasteProcessingDispatcher.class);
        // 복구가 processor를 직접 잡고 있으면 기동 스레드에서 분석이 돈다.
        assertThat(fieldTypes).doesNotContain(WasteSubmissionProcessor.class);
        assertThat(recoveryRunner).isNotNull();
    }
}
