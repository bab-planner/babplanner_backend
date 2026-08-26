package com.babplanner.backend.dev;

import com.babplanner.backend.survey.domain.Survey;
import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyQuestionType;
import com.babplanner.backend.survey.repository.SurveyRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 E2E용 활성 설문 fixture.
 *
 * <p>실제 운영 설문이 아니라 개발용이다. 제목에 그 사실을 적어 둔다.
 *
 * <p>이미 참여 가능한 설문이 있으면 새로 만들지 않는다. 한 번 제출하면 같은
 * 설문은 409가 되므로, E2E를 다시 하려면 이 설문의 기간을 지나가게 하거나
 * 응답을 지운 뒤 재기동해 새 설문을 받는다(완료 보고의 반복 전략 참고).
 */
@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "bapplanner.dev-survey", name = "enabled", havingValue = "true")
public class DevSurveySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevSurveySeeder.class);

    private final SurveyRepository surveyRepository;
    private final Clock clock;

    public DevSurveySeeder(SurveyRepository surveyRepository, Clock clock) {
        this.surveyRepository = surveyRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Instant now = clock.instant();
        if (surveyRepository.findActive(now, Limit.of(1)).isPresent()) {
            return;
        }

        Survey survey = new Survey(
                "[개발 샘플] 오늘의 식사 만족도 조사",
                "개발 환경 확인용 설문입니다.",
                now.minus(1, ChronoUnit.HOURS),
                now.plus(30, ChronoUnit.DAYS),
                now);

        // Flutter가 지원하는 4종 질문을 모두 포함한다.
        survey.addQuestion(new SurveyQuestion(
                "오늘 식사에 얼마나 만족하셨나요?",
                SurveyQuestionType.SCALE,
                true,
                1,
                List.of("매우 만족", "만족", "보통", "불만족", "매우 불만족")));
        survey.addQuestion(new SurveyQuestion(
                "가장 만족한 메뉴는 무엇인가요?",
                SurveyQuestionType.SINGLE_CHOICE,
                true,
                2,
                List.of("제육덮밥 정식", "얼큰 온쫄면 정식", "떡볶이 세트")));
        survey.addQuestion(new SurveyQuestion(
                "개선이 필요한 점을 모두 골라 주세요.",
                SurveyQuestionType.MULTIPLE_CHOICE,
                false,
                3,
                List.of("맛", "양", "위생", "대기 시간")));
        survey.addQuestion(new SurveyQuestion(
                "자유롭게 의견을 남겨 주세요.", SurveyQuestionType.TEXT, false, 4, List.of()));

        surveyRepository.save(survey);
        log.info("개발용 설문 fixture를 생성했습니다.");
    }
}
