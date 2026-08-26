package com.babplanner.backend.dev;

import com.babplanner.backend.user.domain.Role;
import com.babplanner.backend.user.domain.UserEntity;
import com.babplanner.backend.user.repository.UserRepository;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로컬 E2E용 관리자 fixture.
 *
 * <p>local/dev 프로필이면서 {@code bapplanner.dev-admin.enabled=true}일 때만
 * 등록된다. 운영 프로필에서는 bean 자체가 만들어지지 않는다.
 *
 * <p>비밀번호는 운영과 같이 BCrypt로 저장한다. 휴대폰 번호, 비밀번호, 학번,
 * 이름은 어떤 로그 레벨로도 출력하지 않는다.
 */
@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "bapplanner.dev-admin", name = "enabled", havingValue = "true")
public class DevAdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevAdminSeeder.class);

    private final DevAdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevAdminSeeder(
            DevAdminProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        requireValue(properties.getPhone(), "bapplanner.dev-admin.phone");
        requireValue(properties.getPassword(), "bapplanner.dev-admin.password");
        requireValue(properties.getStudentId(), "bapplanner.dev-admin.student-id");
        requireValue(properties.getName(), "bapplanner.dev-admin.name");
    }

    private static void requireValue(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    propertyName + " 가 설정되지 않았습니다. dev-admin을 켰다면 값을 모두 "
                            + "환경 변수로 주입하세요.");
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        userRepository
                .findByPhone(properties.getPhone())
                .ifPresentOrElse(this::checkExisting, this::createAdmin);
    }

    /**
     * 이미 같은 번호의 계정이 있을 때.
     *
     * <p>일반 사용자를 조용히 관리자로 올리지 않는다. 설정 파일 한 줄로 권한이
     * 올라가면 그 일이 언제 일어났는지 아무도 모른다. 기동을 멈추고 사람이
     * 확인하게 한다.
     */
    private void checkExisting(UserEntity existing) {
        if (existing.getRole() != Role.ADMIN) {
            throw new IllegalStateException(
                    "dev-admin 번호로 이미 일반 사용자 계정이 있습니다. 자동으로 권한을 "
                            + "올리지 않습니다. 다른 번호를 쓰거나 해당 계정을 정리하세요.");
        }
        // 이미 관리자다. 프로필을 덮어쓰지 않는다.
    }

    private void createAdmin() {
        userRepository.save(new UserEntity(
                properties.getName(),
                properties.getPhone(),
                properties.getStudentId(),
                null,
                Role.ADMIN,
                passwordEncoder.encode(properties.getPassword()),
                clock.instant()));

        // 어떤 값도 로그에 남기지 않는다.
        log.info("로컬 E2E용 관리자 계정을 생성했습니다.");
    }
}
