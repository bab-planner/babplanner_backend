package com.babplanner.backend.dev.demo;

import com.babplanner.backend.congestion.domain.CongestionLevel;
import com.babplanner.backend.congestion.domain.CongestionObservation;
import com.babplanner.backend.congestion.domain.CongestionStatus;
import com.babplanner.backend.congestion.repository.CongestionObservationRepository;
import com.babplanner.backend.congestion.repository.CongestionStatusRepository;
import com.babplanner.backend.menu.domain.Menu;
import com.babplanner.backend.menu.repository.MenuRepository;
import com.babplanner.backend.reward.config.RewardProperties;
import com.babplanner.backend.reward.domain.RewardAccount;
import com.babplanner.backend.reward.repository.RewardAccountRepository;
import com.babplanner.backend.reward.repository.RewardTransactionRepository;
import com.babplanner.backend.reward.service.RewardService;
import com.babplanner.backend.site.domain.DiningSite;
import com.babplanner.backend.site.repository.DiningSiteRepository;
import com.babplanner.backend.survey.domain.Survey;
import com.babplanner.backend.survey.domain.SurveyAnswer;
import com.babplanner.backend.survey.domain.SurveyQuestion;
import com.babplanner.backend.survey.domain.SurveyQuestionType;
import com.babplanner.backend.survey.domain.SurveyResponse;
import com.babplanner.backend.survey.repository.SurveyRepository;
import com.babplanner.backend.survey.repository.SurveyResponseRepository;
import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import com.babplanner.backend.user.repository.UserRepository;
import com.babplanner.backend.waste.domain.WasteSubmission;
import com.babplanner.backend.waste.domain.WasteSubmissionStatus;
import com.babplanner.backend.waste.repository.WasteSubmissionRepository;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import com.babplanner.backend.waste.storage.WasteImageUpload;
import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 관리자 웹 시연용 샘플 데이터 생성.
 *
 * <p>local/dev 프로필에서만 bean이 만들어진다. 기존 데이터를 지우거나 덮어쓰지 않고, 같은
 * 키(식당·날짜·끼니, 사용자·메뉴, 관측 시각 등)가 이미 있으면 건너뛰므로 반복 실행해도
 * 중복이 쌓이지 않는다. 만든 행은 {@code demo_seed_records}에 등록해 정리 명령이 그 행만
 * 지울 수 있게 한다.
 *
 * <p>가상 데이터를 실제 결과로 위장하지 않는다. 혼잡도 출처는 {@value #SOURCE}이고(CCTV AI가
 * 아니다) 모델 버전을 넣지 않는다. 잔반률은 일부 승인 건에만 시연용 측정값으로 넣고
 * analysis_model_version을 {@value #SOURCE}로 표시한다. 가상 사용자는 로그인할 수 없다.
 *
 * <p>전체가 한 트랜잭션이다. 실패하면 DB는 롤백되고 이미 저장한 샘플 이미지 파일도 지운다.
 * dry-run은 같은 계산을 하고 롤백하며 이미지 파일은 만들지 않는다.
 */
@Service
@Profile({"local", "dev"})
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    /** 혼잡도 관측 출처이자 시연 데이터 표식. 실제 수집기("CCTV_AI")와 구분된다. */
    static final String SOURCE = "DEMO_SEED";

    static final String T_USER = "USER";
    static final String T_MENU = "MENU";
    static final String T_OBSERVATION = "CONGESTION_OBSERVATION";
    static final String T_STATUS = "CONGESTION_STATUS";
    static final String T_WASTE = "WASTE_SUBMISSION";
    static final String T_REWARD_TX = "REWARD_TRANSACTION";
    static final String T_REWARD_ACCOUNT = "REWARD_ACCOUNT";
    static final String T_SURVEY = "SURVEY";
    static final String T_SURVEY_RESPONSE = "SURVEY_RESPONSE";

    static final int DEMO_USERS = 20;
    static final String STUDENT_ID_PREFIX = "DEMO-";
    static final int DAYS = 7;

    /** 잔반 승인 포인트의 출처. AdminWasteSubmissionService와 같은 값이다. */
    private static final String REWARD_REFERENCE_TYPE = "WASTE_SUBMISSION";

    private static final String REWARD_TYPE = "WASTE_APPROVED";
    private static final String REWARD_DESCRIPTION = "잔반 인증 적립";

    static final String SURVEY_A_TITLE = "[시연] 학식 만족도 조사";
    static final String SURVEY_B_TITLE = "[시연] 식당 이용 환경 조사";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    /** 급식 운영 시간대. Clock이 가진 값이며 운영 배포에서는 BAPPLANNER_BUSINESS_ZONE_ID=Asia/Seoul 이다. */
    private final ZoneId zone;
    private final Clock clock;
    private final PasswordEncoder passwordEncoder;
    private final DemoSeedRecordRepository records;
    private final UserRepository userRepository;
    private final DiningSiteRepository siteRepository;
    private final MenuRepository menuRepository;
    private final CongestionObservationRepository observationRepository;
    private final CongestionStatusRepository statusRepository;
    private final WasteSubmissionRepository wasteRepository;
    private final WasteImageStorage imageStorage;
    private final RewardService rewardService;
    private final RewardProperties rewardProperties;
    private final RewardAccountRepository accountRepository;
    private final RewardTransactionRepository transactionRepository;
    private final SurveyRepository surveyRepository;
    private final SurveyResponseRepository surveyResponseRepository;

    public DemoDataSeeder(
            JdbcTemplate jdbc,
            PlatformTransactionManager transactionManager,
            Clock clock,
            PasswordEncoder passwordEncoder,
            DemoSeedRecordRepository records,
            UserRepository userRepository,
            DiningSiteRepository siteRepository,
            MenuRepository menuRepository,
            CongestionObservationRepository observationRepository,
            CongestionStatusRepository statusRepository,
            WasteSubmissionRepository wasteRepository,
            WasteImageStorage imageStorage,
            RewardService rewardService,
            RewardProperties rewardProperties,
            RewardAccountRepository accountRepository,
            RewardTransactionRepository transactionRepository,
            SurveyRepository surveyRepository,
            SurveyResponseRepository surveyResponseRepository) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.zone = clock.getZone();
        this.passwordEncoder = passwordEncoder;
        this.records = records;
        this.userRepository = userRepository;
        this.siteRepository = siteRepository;
        this.menuRepository = menuRepository;
        this.observationRepository = observationRepository;
        this.statusRepository = statusRepository;
        this.wasteRepository = wasteRepository;
        this.imageStorage = imageStorage;
        this.rewardService = rewardService;
        this.rewardProperties = rewardProperties;
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.surveyRepository = surveyRepository;
        this.surveyResponseRepository = surveyResponseRepository;
    }

    /** 시연 데이터를 만든다. 반복 실행해도 같은 결과다. */
    public DemoReport seed(boolean dryRun) {
        List<String> storedImageKeys = new ArrayList<>();
        try {
            return tx.execute(status -> {
                DemoReport report = new DemoReport("seed", dryRun);
                doSeed(report, dryRun, storedImageKeys);
                if (dryRun) {
                    status.setRollbackOnly();
                }
                return report;
            });
        } catch (RuntimeException failure) {
            // DB는 롤백됐다. 파일만 남지 않게 정리한다.
            storedImageKeys.forEach(imageStorage::delete);
            throw failure;
        }
    }

    private void doSeed(DemoReport report, boolean dryRun, List<String> storedImageKeys) {
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, zone);

        // --- 삽입 전 검증: 하나라도 어긋나면 아무것도 쓰지 않고 중단한다 ---
        List<DiningSite> sites = siteRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(DiningSite::isActive)
                .toList();
        if (sites.isEmpty()) {
            throw new IllegalStateException("활성 식당이 없어 시연 데이터를 만들 수 없습니다.");
        }
        List<Long> admins = jdbc.queryForList(
                "SELECT id FROM users WHERE role = 'ADMIN' ORDER BY id", Long.class);
        if (admins.isEmpty()) {
            throw new IllegalStateException(
                    "관리자 계정이 없어 검토 기록을 만들 수 없습니다. 관리자 계정을 먼저 준비하세요.");
        }
        long adminId = admins.get(0);
        int points = rewardProperties.getWasteApprovalPoints();
        if (points < 0) {
            throw new IllegalStateException("잔반 승인 포인트가 음수입니다.");
        }
        verifyNoConflicts();

        // --- 생성 ---
        List<Long> userIds = seedUsers(report, now);
        seedMenus(report, sites, today, now);
        menuRepository.flush();
        seedCongestion(report, sites, today, now);
        seedWaste(report, userIds, adminId, points, today, now, dryRun, storedImageKeys);
        seedSurveys(report, userIds, today, now);
    }

    // ------------------------------------------------------------------ 검증

    /** 가상 사용자와 같은 전화번호/학번이 시연 데이터가 아닌 행에 있으면 중단한다. */
    private void verifyNoConflicts() {
        for (int i = 1; i <= DEMO_USERS; i++) {
            userRepository.findByPhone(phone(i)).ifPresent(user -> {
                if (!records.existsByEntityTypeAndEntityId(T_USER, user.getId())) {
                    throw new IllegalStateException(
                            "가상 사용자 전화번호가 기존 사용자와 충돌합니다. 중단합니다.");
                }
            });
            if (userRepository.existsByStudentId(studentId(i))
                    && userRepository.findByPhone(phone(i)).isEmpty()) {
                throw new IllegalStateException(
                        "가상 사용자 학번이 기존 사용자와 충돌합니다. 중단합니다.");
            }
        }
        for (String title : List.of(SURVEY_A_TITLE, SURVEY_B_TITLE)) {
            for (Long id : jdbc.queryForList(
                    "SELECT id FROM surveys WHERE title = ?", Long.class, title)) {
                if (!records.existsByEntityTypeAndEntityId(T_SURVEY, id)) {
                    throw new IllegalStateException(
                            "같은 제목의 기존 설문이 있어 시연 설문을 만들 수 없습니다. 중단합니다.");
                }
            }
        }
    }

    private static String phone(int index) {
        return String.format("0100000%04d", index);
    }

    private static String studentId(int index) {
        return String.format("%s%03d", STUDENT_ID_PREFIX, index);
    }

    private void register(String type, Long id, Instant now) {
        if (!records.existsByEntityTypeAndEntityId(type, id)) {
            records.save(new DemoSeedRecord(type, id, now));
        }
    }

    private static Random rnd(Object... parts) {
        return new Random(Objects.hash(parts) * 31L + 17L);
    }

    // ------------------------------------------------------------------ 사용자

    private List<Long> seedUsers(DemoReport report, Instant now) {
        List<Long> ids = new ArrayList<>();
        for (int i = 1; i <= DEMO_USERS; i++) {
            var existing = userRepository.findByPhone(phone(i));
            if (existing.isPresent()) {
                ids.add(existing.get().getId());
                report.skipped("users");
                continue;
            }
            // 로그인할 수 없는 계정이다. 무작위 값을 BCrypt로 해시하고 값은 버린다.
            UserEntity user = userRepository.save(new UserEntity(
                    String.format("시연학생%02d", i),
                    phone(i),
                    studentId(i),
                    null,
                    Role.USER,
                    passwordEncoder.encode(UUID.randomUUID().toString()),
                    now));
            register(T_USER, user.getId(), now);
            ids.add(user.getId());
            report.done("users");
        }
        return ids;
    }

    // ------------------------------------------------------------------ 식단

    private void seedMenus(DemoReport report, List<DiningSite> sites, LocalDate today, Instant now) {
        for (int offset = DAYS - 1; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            for (int s = 0; s < sites.size(); s++) {
                DiningSite site = sites.get(s);
                Set<String> occupied = new HashSet<>(jdbc.queryForList(
                        "SELECT DISTINCT meal_type FROM menus WHERE site_id = ? AND service_date = ?",
                        String.class,
                        site.getId(),
                        date));
                for (DemoMenuCatalog.Spec spec : DemoMenuCatalog.forDay(s, date)) {
                    // 이미 그 끼니에 식단이 있으면(기존 데이터, DevMenuSeeder 포함) 건드리지 않는다.
                    if (occupied.contains(spec.mealType().name())) {
                        report.skipped("menus");
                        continue;
                    }
                    Menu menu = new Menu(
                            site,
                            date,
                            spec.mealType(),
                            spec.category(),
                            spec.name(),
                            spec.items(),
                            now);
                    menu.applyNutrition(
                            spec.caloriesKcal(),
                            spec.proteinG(),
                            spec.carbohydrateG(),
                            spec.fatG(),
                            spec.sodiumMg(),
                            now);
                    menuRepository.save(menu);
                    register(T_MENU, menu.getId(), now);
                    report.done("menus");
                }
            }
        }
    }

    // ------------------------------------------------------------------ 혼잡도

    /** 점심 시간대(11:00~14:00, 10분 간격) 관측 수. */
    private static final int OBSERVATION_STEPS = 19;

    private void seedCongestion(
            DemoReport report, List<DiningSite> sites, LocalDate today, Instant now) {
        for (int s = 0; s < sites.size(); s++) {
            DiningSite site = sites.get(s);
            int capacity = s == 0 ? 150 : 90;
            Instant windowStart = today.minusDays(DAYS - 1).atStartOfDay(zone).toInstant();
            Set<Instant> existing = new HashSet<>();
            for (CongestionObservation observation :
                    observationRepository.findBySiteIdAndObservedAtBetweenOrderByObservedAtAscIdAsc(
                            site.getId(), windowStart, now)) {
                if (SOURCE.equals(observation.getSource())) {
                    existing.add(observation.getObservedAt());
                }
            }

            Instant latest = null;
            CongestionLevel latestLevel = null;
            Integer latestCount = null;
            for (int offset = DAYS - 1; offset >= 0; offset--) {
                LocalDate date = today.minusDays(offset);
                int[] counts = lunchCounts(site.getId(), date, capacity);
                for (int i = 0; i < OBSERVATION_STEPS; i++) {
                    Instant observedAt = LocalDateTime.of(date, java.time.LocalTime.of(11, 0))
                            .plusMinutes(10L * i)
                            .atZone(zone)
                            .toInstant();
                    if (observedAt.isAfter(now)) {
                        continue; // 아직 오지 않은 시각의 관측은 만들지 않는다.
                    }
                    CongestionLevel level = levelOf(counts[i], capacity);
                    if (latest == null || observedAt.isAfter(latest)) {
                        latest = observedAt;
                        latestLevel = level;
                        latestCount = counts[i];
                    }
                    if (existing.contains(observedAt)) {
                        report.skipped("congestionObservations");
                        continue;
                    }
                    CongestionObservation saved = observationRepository.save(new CongestionObservation(
                            site,
                            level,
                            counts[i],
                            messageOf(level),
                            SOURCE,
                            "demo-seed",
                            null,
                            observedAt,
                            now));
                    register(T_OBSERVATION, saved.getId(), now);
                    report.done("congestionObservations");
                }
            }
            if (latest != null) {
                syncStatus(report, site, latestLevel, latestCount, latest, now);
            }
        }
    }

    /** 현재값은 이력의 가장 최근 관측과 맞춘다. 실제 수집기가 쓴 현재값은 덮어쓰지 않는다. */
    private void syncStatus(
            DemoReport report,
            DiningSite site,
            CongestionLevel level,
            int count,
            Instant observedAt,
            Instant now) {
        var current = statusRepository.findBySiteId(site.getId());
        if (current.isEmpty()) {
            CongestionStatus saved = statusRepository.save(new CongestionStatus(
                    site, level, count, messageOf(level), SOURCE, observedAt, now));
            register(T_STATUS, saved.getId(), now);
            report.done("congestionStatus");
        } else if (SOURCE.equals(current.get().getSource())
                && observedAt.isAfter(current.get().getObservedAt())) {
            current.get().apply(level, count, messageOf(level), SOURCE, observedAt, now);
            report.done("congestionStatus");
        } else {
            report.skipped("congestionStatus");
        }
    }

    /** 여유 → 보통 → 혼잡 → 보통 → 여유로 오르내리는 인원. 인접 관측 사이의 변화는 제한한다. */
    static int[] lunchCounts(Long siteId, LocalDate date, int capacity) {
        Random random = rnd("congestion", siteId, date);
        double peak = capacity * (0.72 + random.nextDouble() * 0.12);
        int[] counts = new int[OBSERVATION_STEPS];
        int maxStep = (int) Math.round(peak * 0.2);
        for (int i = 0; i < OBSERVATION_STEPS; i++) {
            double shape = Math.pow(Math.sin(Math.PI * i / (OBSERVATION_STEPS - 1)), 1.3);
            double noise = (random.nextDouble() - 0.5) * 0.08 * peak;
            int value = (int) Math.round(peak * shape + noise);
            value = Math.max(value, 6);
            if (i > 0) {
                value = Math.max(counts[i - 1] - maxStep, Math.min(counts[i - 1] + maxStep, value));
            }
            counts[i] = Math.max(value, 1);
        }
        return counts;
    }

    static CongestionLevel levelOf(int people, int capacity) {
        double ratio = (double) people / capacity;
        if (ratio < 0.35) {
            return CongestionLevel.COMFORTABLE;
        }
        if (ratio < 0.65) {
            return CongestionLevel.NORMAL;
        }
        if (ratio < 0.95) {
            return CongestionLevel.CROWDED;
        }
        return CongestionLevel.VERY_CROWDED;
    }

    private static String messageOf(CongestionLevel level) {
        return switch (level) {
            case COMFORTABLE -> "바로 입장 가능";
            case NORMAL -> "대기 약 5분";
            case CROWDED -> "대기 약 10분";
            case VERY_CROWDED -> "대기 15분 이상";
        };
    }

    // ------------------------------------------------------------------ 잔반

    private void seedWaste(
            DemoReport report,
            List<Long> userIds,
            long adminId,
            int points,
            LocalDate today,
            Instant now,
            boolean dryRun,
            List<String> storedImageKeys) {
        for (int offset = DAYS - 1; offset >= 0; offset--) {
            LocalDate date = today.minusDays(offset);
            // 창이 하루씩 밀려도 같은 날짜는 같은 사용자 배정이 되도록 날짜에서 계산한다.
            int dayIndex = (int) Math.floorMod(date.toEpochDay(), 20L);
            List<Map<String, Object>> menus = jdbc.queryForList(
                    "SELECT id, name FROM menus WHERE service_date = ? AND meal_type = 'LUNCH' "
                            + "ORDER BY id",
                    date);
            if (menus.isEmpty()) {
                continue;
            }
            Random dayRandom = rnd("waste-day", date);
            int count = 12 + dayRandom.nextInt(5);
            boolean isToday = offset == 0;

            for (int k = 0; k < count; k++) {
                Map<String, Object> menu = menus.get(k % menus.size());
                long menuId = ((Number) menu.get("id")).longValue();
                String menuName = (String) menu.get("name");
                long userId = userIds.get((k + dayIndex * 5) % userIds.size());

                Integer existing = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM waste_submissions WHERE user_id = ? AND menu_id = ?",
                        Integer.class,
                        userId,
                        menuId);
                if (existing != null && existing > 0) {
                    report.skipped("wasteSubmissions");
                    continue;
                }

                Random random = rnd("waste", date, k);
                Instant created;
                if (isToday) {
                    // 오늘은 항상 "지금"보다 앞선 시각으로 흩어 두어 검토 완료 기록이 존재하게 한다.
                    created = now.minus(Duration.ofMinutes(14L * (count - k)));
                } else {
                    created = LocalDateTime.of(date, java.time.LocalTime.of(11, 40))
                            .plusMinutes(8L * k + random.nextInt(6))
                            .atZone(zone)
                            .toInstant();
                }
                Instant captured = created.minusSeconds(120);

                WasteSubmissionStatus target = pickStatus(random, isToday, k, count);
                Instant reviewedAt = null;
                if (target != WasteSubmissionStatus.PENDING) {
                    reviewedAt = created.plus(Duration.ofMinutes(10 + random.nextInt(80)));
                    Instant limit = now.minusSeconds(60);
                    if (reviewedAt.isAfter(limit)) {
                        reviewedAt = limit;
                    }
                    if (!reviewedAt.isAfter(created)) {
                        target = WasteSubmissionStatus.PENDING;
                        reviewedAt = null;
                    }
                }

                Double wasteRate = null;
                if (target == WasteSubmissionStatus.APPROVED && random.nextDouble() < 0.75) {
                    double base = 0.10 + (Math.floorMod(menuName.hashCode(), 45)) / 100.0;
                    double value = base + (random.nextDouble() - 0.5) * 0.16;
                    wasteRate = Math.round(Math.max(0.02, Math.min(0.90, value)) * 100) / 100.0;
                }

                String key = "dry-run";
                if (!dryRun) {
                    byte[] png = DemoPng.generate(
                            Objects.hash(date, k, menuId), wasteRate != null ? wasteRate : 0.3);
                    key = imageStorage
                            .store(new WasteImageUpload(
                                    () -> new ByteArrayInputStream(png),
                                    "demo-sample.png",
                                    "image/png",
                                    png.length))
                            .storageKey();
                    storedImageKeys.add(key);
                }

                WasteSubmission submission =
                        wasteRepository.saveAndFlush(new WasteSubmission(userId, menuId, key, captured, created));
                register(T_WASTE, submission.getId(), now);
                report.done("wasteSubmissions");

                if (target == WasteSubmissionStatus.APPROVED) {
                    approve(report, submission, userId, adminId, points, wasteRate, reviewedAt, now);
                } else if (target == WasteSubmissionStatus.REJECTED) {
                    submission.markReviewed(WasteSubmissionStatus.REJECTED, 0, adminId, reviewedAt);
                    report.done("wasteRejected");
                } else {
                    report.done("wastePending");
                }
            }
        }
        wasteRepository.flush();
    }

    private WasteSubmissionStatus pickStatus(Random random, boolean isToday, int k, int count) {
        double roll = random.nextDouble();
        if (isToday) {
            // 오늘 앞쪽 건은 검토 완료, 뒤쪽 일부는 대기로 남겨 검토 화면을 보여 준다.
            if (k < Math.round(count * 0.65)) {
                return roll < 0.8 ? WasteSubmissionStatus.APPROVED : WasteSubmissionStatus.REJECTED;
            }
            return WasteSubmissionStatus.PENDING;
        }
        if (roll < 0.62) {
            return WasteSubmissionStatus.APPROVED;
        }
        return roll < 0.82 ? WasteSubmissionStatus.REJECTED : WasteSubmissionStatus.PENDING;
    }

    /** 실제 승인과 같은 규칙: 제출 id를 출처로 한 번만 지급한다. */
    private void approve(
            DemoReport report,
            WasteSubmission submission,
            long userId,
            long adminId,
            int points,
            Double wasteRate,
            Instant reviewedAt,
            Instant now) {
        if (accountRepository.findByUserId(userId).isEmpty()) {
            RewardAccount account = accountRepository.saveAndFlush(new RewardAccount(userId, 0, now));
            register(T_REWARD_ACCOUNT, account.getId(), now);
            report.done("rewardAccounts");
        }
        int credited = rewardService.creditOnce(
                userId,
                points,
                REWARD_TYPE,
                REWARD_DESCRIPTION,
                REWARD_REFERENCE_TYPE,
                submission.getId());
        if (wasteRate != null) {
            submission.applyMeasurement(wasteRate, null, reviewedAt);
        }
        submission.markReviewed(WasteSubmissionStatus.APPROVED, credited, adminId, reviewedAt);
        wasteRepository.flush();
        if (wasteRate != null) {
            // 시연용 측정값임을 표시한다. 실제 분석 모델 버전을 쓰지 않는다.
            jdbc.update(
                    "UPDATE waste_submissions SET analysis_model_version = ? WHERE id = ?",
                    SOURCE,
                    submission.getId());
            report.done("wasteDemoMeasurements");
        }
        transactionRepository
                .findByReferenceTypeAndReferenceId(REWARD_REFERENCE_TYPE, submission.getId())
                .ifPresent(transaction -> register(T_REWARD_TX, transaction.getId(), now));
        report.done("wasteApproved");
    }

    // ------------------------------------------------------------------ 설문

    private void seedSurveys(DemoReport report, List<Long> userIds, LocalDate today, Instant now) {
        Instant start = today.minusDays(8).atStartOfDay(zone).toInstant();
        // 이미 끝난 설문이다. 실제 앱 사용자에게 참여 가능한 설문으로 노출되지 않는다.
        Instant end = today.atStartOfDay(zone).toInstant();

        Survey a = findOrCreateSurvey(
                report,
                SURVEY_A_TITLE,
                start,
                end,
                now,
                List.of(
                        new SurveyQuestion(
                                "오늘 식사에 얼마나 만족하셨나요?",
                                SurveyQuestionType.SCALE,
                                true,
                                1,
                                List.of("매우 불만족", "불만족", "보통", "만족", "매우 만족")),
                        new SurveyQuestion(
                                "가장 만족한 메뉴 종류는 무엇인가요?",
                                SurveyQuestionType.SINGLE_CHOICE,
                                true,
                                2,
                                List.of("한식", "분식", "양식", "중식·일식")),
                        new SurveyQuestion(
                                "개선이 필요한 점을 모두 골라 주세요.",
                                SurveyQuestionType.MULTIPLE_CHOICE,
                                false,
                                3,
                                List.of("맛", "양", "위생", "대기 시간")),
                        new SurveyQuestion(
                                "자유롭게 의견을 남겨 주세요.",
                                SurveyQuestionType.TEXT,
                                false,
                                4,
                                List.of())));
        Survey b = findOrCreateSurvey(
                report,
                SURVEY_B_TITLE,
                start,
                end,
                now,
                List.of(
                        new SurveyQuestion(
                                "식당 환경(청결·좌석)에 만족하시나요?",
                                SurveyQuestionType.SCALE,
                                true,
                                1,
                                List.of("매우 불만족", "불만족", "보통", "만족", "매우 만족")),
                        new SurveyQuestion(
                                "배식 대기 시간은 어느 정도였나요?",
                                SurveyQuestionType.SINGLE_CHOICE,
                                true,
                                2,
                                List.of("5분 이내", "5~10분", "10~20분", "20분 이상")),
                        new SurveyQuestion(
                                "식당 이용 중 불편했던 점이 있다면 알려 주세요.",
                                SurveyQuestionType.TEXT,
                                false,
                                3,
                                List.of())));

        String[] comments = {
            "반찬 가짓수가 늘었으면 좋겠어요.",
            "점심시간에 줄이 너무 길어요.",
            "국이 조금 짠 편이에요.",
            "메뉴가 다양해서 좋습니다.",
            "분식 메뉴를 더 자주 내 주세요.",
            "양이 넉넉해서 만족합니다.",
            "자리가 부족할 때가 있어요.",
            "채식 메뉴도 있으면 좋겠습니다.",
            "배식 속도가 빨라졌네요.",
            "후식이 있으면 좋겠어요."
        };

        int[] scaleA = {1, 3, 7, 8, 4};
        int[] scaleB = {2, 4, 6, 5, 2};
        int[] kindWeights = {10, 5, 4, 3};
        int[] waitWeights = {5, 8, 7, 2};

        for (int i = 0; i < userIds.size(); i++) {
            long userId = userIds.get(i);
            if (responseMissing(a, userId, report)) {
                Random random = rnd("survey-a", userId);
                SurveyResponse response =
                        new SurveyResponse(a.getId(), userId, submittedAt(random, start, end));
                List<SurveyQuestion> q = a.getQuestions();
                response.addAnswer(new SurveyAnswer(q.get(0).getId(), List.of(), weighted(random, scaleA) + 1, null));
                response.addAnswer(new SurveyAnswer(
                        q.get(1).getId(), List.of(q.get(1).getOptions().get(weighted(random, kindWeights))), null, null));
                if (random.nextDouble() < 0.8) {
                    List<String> options = q.get(2).getOptions();
                    int first = random.nextInt(options.size());
                    List<String> chosen = new ArrayList<>(List.of(options.get(first)));
                    if (random.nextBoolean()) {
                        String second = options.get((first + 1 + random.nextInt(options.size() - 1)) % options.size());
                        chosen.add(second);
                    }
                    response.addAnswer(new SurveyAnswer(q.get(2).getId(), chosen, null, null));
                }
                if (random.nextDouble() < 0.5) {
                    response.addAnswer(new SurveyAnswer(
                            q.get(3).getId(), List.of(), null, comments[random.nextInt(comments.length)]));
                }
                saveResponse(response, now, report);
            }
            if (i < 18 && responseMissing(b, userId, report)) {
                Random random = rnd("survey-b", userId);
                SurveyResponse response =
                        new SurveyResponse(b.getId(), userId, submittedAt(random, start, end));
                List<SurveyQuestion> q = b.getQuestions();
                response.addAnswer(new SurveyAnswer(q.get(0).getId(), List.of(), weighted(random, scaleB) + 1, null));
                response.addAnswer(new SurveyAnswer(
                        q.get(1).getId(), List.of(q.get(1).getOptions().get(weighted(random, waitWeights))), null, null));
                if (random.nextDouble() < 0.4) {
                    response.addAnswer(new SurveyAnswer(
                            q.get(2).getId(), List.of(), null, comments[random.nextInt(comments.length)]));
                }
                saveResponse(response, now, report);
            }
        }
    }

    private Survey findOrCreateSurvey(
            DemoReport report,
            String title,
            Instant start,
            Instant end,
            Instant now,
            List<SurveyQuestion> questions) {
        List<Long> found =
                jdbc.queryForList("SELECT id FROM surveys WHERE title = ?", Long.class, title);
        if (!found.isEmpty()) {
            report.skipped("surveys");
            return surveyRepository.findWithQuestionsById(found.get(0)).orElseThrow();
        }
        Survey survey = new Survey(title, "시연용 가상 응답 데이터입니다.", start, end, now);
        questions.forEach(survey::addQuestion);
        surveyRepository.saveAndFlush(survey);
        register(T_SURVEY, survey.getId(), now);
        report.done("surveys");
        return surveyRepository.findWithQuestionsById(survey.getId()).orElseThrow();
    }

    private boolean responseMissing(Survey survey, long userId, DemoReport report) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM survey_responses WHERE survey_id = ? AND user_id = ?",
                Integer.class,
                survey.getId(),
                userId);
        if (count != null && count > 0) {
            report.skipped("surveyResponses");
            return false;
        }
        return true;
    }

    private void saveResponse(SurveyResponse response, Instant now, DemoReport report) {
        surveyResponseRepository.saveAndFlush(response);
        register(T_SURVEY_RESPONSE, response.getId(), now);
        report.done("surveyResponses");
    }

    private static Instant submittedAt(Random random, Instant start, Instant end) {
        long span = Duration.between(start, end).toMinutes() - 120;
        return start.plus(Duration.ofMinutes(60 + (long) (random.nextDouble() * span)));
    }

    /** 가중치 배열에서 인덱스를 고른다. */
    private static int weighted(Random random, int[] weights) {
        int total = 0;
        for (int w : weights) {
            total += w;
        }
        int pick = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return i;
            }
        }
        return weights.length - 1;
    }
}
