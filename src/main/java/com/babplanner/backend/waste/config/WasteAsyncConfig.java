package com.babplanner.backend.waste.config;

import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 잔반 분석 전용 실행기.
 *
 * <p>공용 executor를 쓰지 않는다. 분석이 밀려도 다른 비동기 작업이 굶지
 * 않게 한다. 큐를 유한하게 두고, 가득 차면 호출 스레드가 직접 실행해
 * 요청을 조용히 버리지 않는다.
 */
@Configuration
@EnableAsync
public class WasteAsyncConfig {

    public static final String EXECUTOR = "wasteAnalysisExecutor";

    @Bean(name = EXECUTOR)
    public Executor wasteAnalysisExecutor(WasteProperties properties) {
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
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        // 종료 중 진행 중인 분석은 마치게 한다. 남은 건은 재시작 복구가 집는다.
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
