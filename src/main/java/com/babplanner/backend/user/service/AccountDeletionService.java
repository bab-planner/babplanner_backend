package com.babplanner.backend.user.service;

import com.babplanner.backend.common.error.ApiException;
import com.babplanner.backend.common.error.ErrorCode;
import com.babplanner.backend.user.domain.UserEntity;
import com.babplanner.backend.user.repository.UserRepository;
import com.babplanner.backend.waste.storage.WasteImageStorage;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 회원 탈퇴.
 *
 * <p>FK에 ON DELETE CASCADE가 없으므로 자식 테이블부터 명시적으로 지운다. 모든
 * DB 삭제는 한 트랜잭션이며, 이미지 파일은 커밋 이후에 best-effort로 지운다.
 * 파일 삭제 실패가 탈퇴를 되돌리지 않는다.
 */
@Service
public class AccountDeletionService {

    private static final Logger log = LoggerFactory.getLogger(AccountDeletionService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JdbcTemplate jdbc;
    private final WasteImageStorage imageStorage;
    private final TransactionTemplate transaction;

    public AccountDeletionService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JdbcTemplate jdbc,
            WasteImageStorage imageStorage,
            PlatformTransactionManager transactionManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jdbc = jdbc;
        this.imageStorage = imageStorage;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    public void delete(Long userId, String password) {
        UserEntity user = userRepository
                .findById(userId)
                .orElseThrow(() -> new ApiException(ErrorCode.UNAUTHORIZED));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            // 401은 앱이 세션 만료로 해석하므로 400을 쓴다.
            throw new ApiException(ErrorCode.INVALID_PASSWORD);
        }

        List<String> imageKeys = transaction.execute(status -> deleteRows(user));

        deleteImages(imageKeys);
    }

    private List<String> deleteRows(UserEntity user) {
        Long id = user.getId();
        // 키는 행을 지우기 전에 모은다.
        List<String> imageKeys = jdbc.queryForList(
                "select image_storage_key from waste_submissions where user_id = ?",
                String.class,
                id);

        jdbc.update("delete from survey_answer_options where answer_id in ("
                + "select a.id from survey_answers a "
                + "join survey_responses r on a.response_id = r.id where r.user_id = ?)", id);
        jdbc.update("delete from survey_answers where response_id in ("
                + "select id from survey_responses where user_id = ?)", id);
        jdbc.update("delete from survey_responses where user_id = ?", id);
        jdbc.update("delete from waste_submissions where user_id = ?", id);
        jdbc.update("delete from inquiries where user_id = ?", id);
        jdbc.update("delete from coupons where user_id = ?", id);
        jdbc.update("delete from reward_transactions where user_id = ?", id);
        jdbc.update("delete from reward_accounts where user_id = ?", id);
        jdbc.update("delete from user_allergies where user_id = ?", id);
        jdbc.update("delete from refresh_sessions where user_id = ?", id);
        jdbc.update("delete from phone_verifications where phone = ?", user.getPhone());
        jdbc.update("delete from users where id = ?", id);
        return imageKeys;
    }

    private void deleteImages(List<String> imageKeys) {
        if (imageKeys == null) {
            return;
        }
        int failed = 0;
        for (String key : imageKeys) {
            try {
                imageStorage.delete(key);
            } catch (RuntimeException exception) {
                failed++;
            }
        }
        if (failed > 0) {
            // 개인 정보와 키는 남기지 않고 개수만 기록한다.
            log.warn("탈퇴한 사용자의 잔반 이미지 {}건을 삭제하지 못했습니다.", failed);
        }
    }
}
