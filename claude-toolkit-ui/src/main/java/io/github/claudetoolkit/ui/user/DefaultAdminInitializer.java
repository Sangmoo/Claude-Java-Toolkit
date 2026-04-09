package io.github.claudetoolkit.ui.user;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 최초 실행 시 기본 관리자 계정(admin/admin1234) 자동 생성.
 * 기존 사용자가 있으면 아무 작업도 하지 않음 (재빌드 시 데이터 보존).
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
        long count = 0;
        try {
            count = userRepository.count();
        } catch (Exception e) {
            // 테이블이 아직 생성되지 않은 경우 (첫 실행)
            System.out.println("[Security] 사용자 테이블 초기화 중...");
        }
        if (count == 0) {
            try {
                AppUser admin = new AppUser("admin", encoder.encode("admin1234"), "ADMIN");
                admin.setDisplayName("관리자");
                admin.setMustChangePassword(true);
                userRepository.save(admin);
                System.out.println("[Security] 기본 관리자 계정 생성됨: admin / admin1234");
            } catch (Exception e) {
                System.out.println("[Security] 관리자 계정 생성 실패 (이미 존재할 수 있음): " + e.getMessage());
            }
        } else {
            System.out.println("[Security] 기존 사용자 " + count + "명 확인됨 — 관리자 계정 재생성 건너뜀");
        }
    }
}
