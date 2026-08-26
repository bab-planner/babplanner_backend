package com.babplanner.backend.waste.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 잔반 분석 전용 실행기.
 *
 * <p>공용 executor를 쓰지 않는다. 분석이 밀려도 다른 비동기 작업이 굶지
 * 않게 한다.
 *
 * <p>큐를 유한하게 두고, 가득 차면 작업을 거절한다. 호출 스레드가 대신
 * 실행하게 두면 업로드 요청이 분석이 끝날 때까지 막혀 201 RECEIVED 후
 * polling이라는 계약이 깨진다. 조용히 버리지도 않는다. 거절은
 * WasteProcessingDispatcher가 받아 그 제출을 실패로 끝낸다.
 */
@Configuration
public class WasteAsyncConfig {

    public static final String EXECUTOR = "wasteAnalysisExecutor";

    @Bean(name = EXECUTOR)
    public TaskExecutor wasteAnalysisExecutor(WasteProperties properties) {
        if (!properties.isAsyncEnabled()) {
            // 호출 스레드에서 그대로 실행한다. 결과를 기다리느라 sleep을 넣지
            // 않아도 되는 테스트용 설정이다.
            return new SyncTaskExecutor();
        }
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("waste-analysis-");
        // 거절만 한다. 호출 스레드 실행(CallerRuns)도, 조용한 폐기(Discard)도 쓰지 않는다.
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        // 종료 중 진행 중인 분석은 마치게 한다. 남은 건은 재시작 복구가 집는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
