package io.github.claudetoolkit.ui.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 최초 실행 시 기본 관리자 계정(admin/admin1234) 자동 생성.
 */
@Component
public class DefaultAdminInitializer implements ApplicationRunner {

    private final AppUserRepository userRepository;
    private final BCryptPasswordEncoder encoder;

    public DefaultAdminInitializer(AppUserRepository userRepository, BCryptPasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.encoder        = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.count() == 0) {
            AppUser admin = new AppUser("admin", encoder.encode("admin1234"), "ADMIN");
            userRepository.save(admin);
            System.out.println("[Security] 기본 관리자 계정 생성됨: admin / admin1234");
        }
    }
}
