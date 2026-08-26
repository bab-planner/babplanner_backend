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
 * 로컬 E2E용 사용자 fixture.
 *
 * <p>local/dev 프로필이면서 {@code bapplanner.dev-user.enabled=true}일 때만
 * 등록된다. 운영 프로필에서는 bean 자체가 만들어지지 않는다.
 *
 * <p>비밀번호는 운영과 동일하게 BCrypt로 저장한다. 휴대폰 번호, 비밀번호,
 * 학번, 이름은 어떤 로그 레벨로도 출력하지 않는다.
 *
 * <p>같은 번호가 이미 있으면 아무것도 하지 않는다. 재시작할 때마다 계정을
 * 덮어써서 프로필 수정 결과가 되돌아가는 일이 없어야 한다.
 */
@Component
@Profile({"local", "dev"})
@ConditionalOnProperty(prefix = "bapplanner.dev-user", name = "enabled", havingValue = "true")
public class DevUserSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevUserSeeder.class);

    private final DevUserProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public DevUserSeeder(
            DevUserProperties properties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            Clock clock) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        requireValue(properties.getPhone(), "bapplanner.dev-user.phone");
        requireValue(properties.getPassword(), "bapplanner.dev-user.password");
        requireValue(properties.getStudentId(), "bapplanner.dev-user.student-id");
        requireValue(properties.getName(), "bapplanner.dev-user.name");
    }

    private static void requireValue(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(
                    propertyName + " 가 설정되지 않았습니다. dev-user를 켰다면 값을 모두 환경 변수로 주입하세요.");
        }
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.existsByPhone(properties.getPhone())) {
            // 이미 있는 계정을 덮어쓰지 않는다. 프로필/알레르기 변경이 유지되어야 한다.
            return;
        }

        userRepository.save(new UserEntity(
                properties.getName(),
                properties.getPhone(),
                properties.getStudentId(),
                null,
                Role.USER,
                passwordEncoder.encode(properties.getPassword()),
                clock.instant()));

        // 어떤 값도 로그에 남기지 않는다.
        log.info("로컬 E2E용 개발 사용자를 생성했습니다.");
    }
}
