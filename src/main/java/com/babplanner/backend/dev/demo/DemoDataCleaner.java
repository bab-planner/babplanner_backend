package com.babplanner.backend.dev.demo;

import com.babplanner.backend.waste.storage.WasteImageStorage;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시연 데이터 선별 삭제.
 *
 * <p>{@code demo_seed_records}에 등록된 행만, 다른 행이 참조하지 않을 때만 지운다. 실제 사용자·관리자·
 * 식단·잔반은 등록된 적이 없으므로 대상이 될 수 없다. TRUNCATE/DROP과 조건 없는 DELETE는 쓰지
 * 않는다. 샘플 이미지 파일은 DB 삭제가 커밋된 뒤에 지운다.
 */
@Service
@Profile({"local", "dev"})
public class DemoDataCleaner {

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final WasteImageStorage imageStorage;

    public DemoDataCleaner(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            WasteImageStorage imageStorage) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.imageStorage = imageStorage;
    }

    private static String registered(String type) {
        return "(SELECT entity_id FROM demo_seed_records WHERE entity_type = '" + type + "')";
    }

    public DemoReport cleanup(boolean dryRun) {
        List<String> imageKeys = new ArrayList<>();
        DemoReport report = tx.execute(status -> {
            DemoReport result = new DemoReport("cleanup", dryRun);
            doCleanup(result, imageKeys);
            if (dryRun) {
                status.setRollbackOnly();
            }
            return result;
        });
        if (!dryRun) {
            imageKeys.forEach(imageStorage::delete);
        }
        return report;
    }

    private void doCleanup(DemoReport report, List<String> imageKeys) {
        String waste = registered(DemoDataSeeder.T_WASTE);
        String responses = registered(DemoDataSeeder.T_SURVEY_RESPONSE);

        // 설문 응답 → 응답 → (남은 응답이 없는) 설문
        jdbc.update("DELETE FROM survey_answer_options WHERE answer_id IN "
                + "(SELECT a.id FROM survey_answers a WHERE a.response_id IN " + responses + ")");
        jdbc.update("DELETE FROM survey_answers WHERE response_id IN " + responses);
        report.done("surveyResponses",
                jdbc.update("DELETE FROM survey_responses WHERE id IN " + responses));
        String deletableSurveys = "(SELECT entity_id FROM demo_seed_records WHERE entity_type = 'SURVEY' "
                + "AND entity_id NOT IN (SELECT survey_id FROM survey_responses))";
        jdbc.update("DELETE FROM survey_question_options WHERE question_id IN "
                + "(SELECT q.id FROM survey_questions q WHERE q.survey_id IN " + deletableSurveys + ")");
        jdbc.update("DELETE FROM survey_questions WHERE survey_id IN " + deletableSurveys);
        report.done("surveys", jdbc.update("DELETE FROM surveys WHERE id IN " + deletableSurveys));

        // 잔반 제출(사진 키는 커밋 뒤 삭제) → 리워드 원장/계정
        imageKeys.addAll(jdbc.queryForList(
                "SELECT image_storage_key FROM waste_submissions WHERE id IN " + waste, String.class));
        report.done("wasteSubmissions",
                jdbc.update("DELETE FROM waste_submissions WHERE id IN " + waste));
        report.done("rewardTransactions", jdbc.update(
                "DELETE FROM reward_transactions WHERE id IN " + registered(DemoDataSeeder.T_REWARD_TX)));
        report.done("rewardAccounts", jdbc.update(
                "DELETE FROM reward_accounts WHERE id IN " + registered(DemoDataSeeder.T_REWARD_ACCOUNT)));

        // 혼잡도: 등록되어 있고 출처도 시연용인 행만
        report.done("congestionObservations", jdbc.update(
                "DELETE FROM congestion_observations WHERE source = ? AND id IN "
                        + registered(DemoDataSeeder.T_OBSERVATION),
                DemoDataSeeder.SOURCE));
        report.done("congestionStatus", jdbc.update(
                "DELETE FROM congestion_status WHERE source = ? AND id IN "
                        + registered(DemoDataSeeder.T_STATUS),
                DemoDataSeeder.SOURCE));

        // 식단: 다른 제출이 아직 가리키는 메뉴는 남긴다
        String deletableMenus = "(SELECT entity_id FROM demo_seed_records WHERE entity_type = 'MENU' "
                + "AND entity_id NOT IN (SELECT menu_id FROM waste_submissions))";
        jdbc.update("DELETE FROM menu_items WHERE menu_id IN " + deletableMenus);
        jdbc.update("DELETE FROM menu_allergies WHERE menu_id IN " + deletableMenus);
        report.done("menus", jdbc.update("DELETE FROM menus WHERE id IN " + deletableMenus));

        // 가상 사용자: 가상 학번의 일반 사용자이고 참조하는 행이 더 없을 때만
        report.done("users", jdbc.update(
                "DELETE FROM users WHERE role = 'USER' AND student_id LIKE '"
                        + DemoDataSeeder.STUDENT_ID_PREFIX + "%' AND id IN "
                        + registered(DemoDataSeeder.T_USER)
                        + " AND id NOT IN (SELECT user_id FROM waste_submissions)"
                        + " AND id NOT IN (SELECT user_id FROM survey_responses)"
                        + " AND id NOT IN (SELECT user_id FROM reward_accounts)"
                        + " AND id NOT IN (SELECT user_id FROM reward_transactions)"
                        + " AND id NOT IN (SELECT user_id FROM coupons)"
                        + " AND id NOT IN (SELECT user_id FROM inquiries)"
                        + " AND id NOT IN (SELECT user_id FROM user_allergies)"
                        + " AND id NOT IN (SELECT user_id FROM refresh_sessions)"));

        // 사라진 행의 생성 기록 정리
        for (String[] pair : new String[][] {
            {DemoDataSeeder.T_USER, "users"},
            {DemoDataSeeder.T_MENU, "menus"},
            {DemoDataSeeder.T_OBSERVATION, "congestion_observations"},
            {DemoDataSeeder.T_STATUS, "congestion_status"},
            {DemoDataSeeder.T_WASTE, "waste_submissions"},
            {DemoDataSeeder.T_REWARD_TX, "reward_transactions"},
            {DemoDataSeeder.T_REWARD_ACCOUNT, "reward_accounts"},
            {DemoDataSeeder.T_SURVEY, "surveys"},
            {DemoDataSeeder.T_SURVEY_RESPONSE, "survey_responses"}
        }) {
            jdbc.update(
                    "DELETE FROM demo_seed_records WHERE entity_type = ? AND entity_id NOT IN "
                            + "(SELECT id FROM " + pair[1] + ")",
                    pair[0]);
        }
    }
}
