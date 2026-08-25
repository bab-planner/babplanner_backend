package com.babplanner.backend.dev;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 오늘 날짜 식단을 채워 두는 개발 전용 시더.
 *
 * <p>{@code GET /menus}는 date가 필수라 앱이 오늘 날짜를 요청한다. 그런데
 * production migration에는 재현 가능성을 위해 고정 날짜 샘플만 넣는다. 그래서
 * 로컬/개발 환경에서만 기동 시 고정 샘플을 오늘 날짜로 복제한다.
 *
 * <p>local/dev 프로필에서만 동작하며, 오늘 데이터가 이미 있으면 아무것도 하지
 * 않는다. 운영 프로필에서는 이 bean 자체가 만들어지지 않는다.
 */
@Component
@Profile({"local", "dev"})
public class DevMenuSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevMenuSeeder.class);

    /** V11 migration이 넣는 고정 샘플 날짜. */
    private static final LocalDate SAMPLE_DATE = LocalDate.of(2026, 8, 26);

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    public DevMenuSeeder(JdbcTemplate jdbcTemplate, Clock clock) {
        this.jdbcTemplate = jdbcTemplate;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        LocalDate today = LocalDate.ofInstant(clock.instant(), ZoneOffset.UTC);
        if (today.equals(SAMPLE_DATE)) {
            return;
        }

        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM menus WHERE service_date = ?", Integer.class, today);
        if (existing != null && existing > 0) {
            return;
        }

        jdbcTemplate.update(
                """
                INSERT INTO menus (site_id, service_date, meal_type, category, name, description,
                                   image_url, calories_kcal, protein_g, carbohydrate_g, fat_g,
                                   sodium_mg, created_at, updated_at)
                SELECT s.site_id, ?, s.meal_type, s.category, s.name, s.description,
                       s.image_url, s.calories_kcal, s.protein_g, s.carbohydrate_g, s.fat_g,
                       s.sodium_mg, s.created_at, s.updated_at
                FROM menus s WHERE s.service_date = ?
                """,
                today,
                SAMPLE_DATE);

        // 샘플 날짜 안에서 name이 유일하므로 name으로 원본을 되찾는다.
        jdbcTemplate.update(
                """
                INSERT INTO menu_items (menu_id, sort_order, name)
                SELECT t.id, i.sort_order, i.name
                FROM menus t
                JOIN menus s ON s.name = t.name AND s.service_date = ?
                JOIN menu_items i ON i.menu_id = s.id
                WHERE t.service_date = ?
                """,
                SAMPLE_DATE,
                today);

        jdbcTemplate.update(
                """
                INSERT INTO menu_allergies (menu_id, allergy_id)
                SELECT t.id, ma.allergy_id
                FROM menus t
                JOIN menus s ON s.name = t.name AND s.service_date = ?
                JOIN menu_allergies ma ON ma.menu_id = s.id
                WHERE t.service_date = ?
                """,
                SAMPLE_DATE,
                today);

        log.info("개발용 식단 샘플을 오늘 날짜로 복제했습니다.");
    }
}
