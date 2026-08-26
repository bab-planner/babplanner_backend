package com.babplanner.backend.waste.service;

import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.repository.WasteSubmissionRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * 재시작 시 진행 중이던 제출을 이어받는다.
 *
 * <p>비동기 처리 도중 서버가 내려가면 RECEIVED/VERIFYING/ANALYZING 상태의
 * 행이 남는다. 아무도 이어받지 않으면 사용자 화면은 영원히 대기한다.
 *
 * <p>이미 종료된 건은 손대지 않는다. 선점은 원자적 UPDATE로 하므로 여러
 * 인스턴스가 동시에 떠도 한 건을 두 번 처리하지 않는다.
 */
@Component
public class WasteRecoveryRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(WasteRecoveryRunner.class);

    private final WasteSubmissionRepository submissionRepository;
    private final WasteSubmissionStateStore stateStore;
    private final WasteSubmissionProcessor processor;

    public WasteRecoveryRunner(
            WasteSubmissionRepository submissionRepository,
            WasteSubmissionStateStore stateStore,
            WasteSubmissionProcessor processor) {
        this.submissionRepository = submissionRepository;
        this.stateStore = stateStore;
        this.processor = processor;
    }

    @Override
    public void run(ApplicationArguments args) {
        recover();
    }

    /**
     * 미완료 건을 다시 처리한다.
     *
     * <p>중간 상태로 멈춘 건은 RECEIVED로 되돌린 뒤 처음부터 다시 진행한다.
     * 어디까지 진행됐는지 알 수 없는 상태를 이어 붙이는 것보다 안전하다.
     *
     * @return 이어받은 건수
     */
    public int recover() {
        List<WasteSubmission> pending = submissionRepository.findByStatusIn(List.of(
                WasteSubmissionStatus.RECEIVED,
                WasteSubmissionStatus.VERIFYING,
                WasteSubmissionStatus.ANALYZING));
        int resumed = 0;
        for (WasteSubmission submission : pending) {
            Long id = submission.getId();
            WasteSubmissionStatus status = submission.getStatus();
            if (status != WasteSubmissionStatus.RECEIVED
                    && !stateStore.claim(id, status, WasteSubmissionStatus.RECEIVED)) {
                // 다른 쪽이 이미 이어받았다.
                continue;
            }
            processor.process(id);
            resumed++;
        }
        if (resumed > 0) {
            log.info("중단된 잔반 제출 {}건을 이어서 처리했습니다.", resumed);
        }
        return resumed;
    }
}
