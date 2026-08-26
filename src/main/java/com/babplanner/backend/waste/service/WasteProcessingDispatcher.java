package com.babplanner.backend.waste.service;

import com.babplanner.backend.waste.config.WasteAsyncConfig;
import java.util.concurrent.RejectedExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 분석 작업을 작업 스레드로 넘기는 유일한 통로.
 *
 * <p>정상 제출과 재시작 복구가 같은 통로를 쓴다. 통로가 둘이면 한쪽만
 * 포화 정책을 지키는 상황이 생긴다.
 *
 * <p>넘기는 것까지만 여기서 하고 실제 처리는 executor 작업 스레드에서만
 * 일어난다. 요청 스레드가 분석을 대신 실행하면 업로드 응답이 분석이 끝날
 * 때까지 막히고, 201 RECEIVED 후 polling이라는 계약이 깨진다.
 *
 * <p>{@code @Async}를 쓰지 않는 이유도 같다. 그 경로에서는 executor가
 * 작업을 거절할 때 예외가 프록시 안쪽에서 처리되어, 거절된 제출을 우리가
 * 마무리할 수 없다.
 */
@Component
public class WasteProcessingDispatcher {

    private static final Logger log = LoggerFactory.getLogger(WasteProcessingDispatcher.class);

    private final TaskExecutor taskExecutor;
    private final WasteSubmissionProcessor processor;
    private final WasteSubmissionStateStore stateStore;

    public WasteProcessingDispatcher(
            @Qualifier(WasteAsyncConfig.EXECUTOR) TaskExecutor taskExecutor,
            WasteSubmissionProcessor processor,
            WasteSubmissionStateStore stateStore) {
        this.taskExecutor = taskExecutor;
        this.processor = processor;
        this.stateStore = stateStore;
    }

    /** 커밋된 뒤에만 시작한다. 커밋 전이면 아직 없는 행을 읽게 된다. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSubmissionCreated(WasteSubmissionCreatedEvent event) {
        dispatch(event.submissionId());
    }

    /**
     * 한 건을 작업 스레드로 넘긴다.
     *
     * <p>큐가 가득 차 거절되면 호출 스레드에서 대신 실행하지 않는다. 대신
     * 그 제출을 실패로 끝낸다. 넘기지도 못한 건을 RECEIVED로 두면 사용자
     * 화면이 영원히 대기한다.
     */
    public void dispatch(Long submissionId) {
        try {
            taskExecutor.execute(() -> processor.process(submissionId));
        } catch (RejectedExecutionException exception) {
            // executor/큐/스레드 같은 내부 사정은 응답에 나가지 않는다.
            log.warn("잔반 분석을 시작하지 못했습니다. id={}", submissionId);
            failQuietly(submissionId);
        }
    }

    private void failQuietly(Long submissionId) {
        try {
            stateStore.fail(submissionId);
        } catch (RuntimeException exception) {
            log.error("잔반 제출을 실패 상태로 기록하지 못했습니다. id={}", submissionId);
        }
    }
}
