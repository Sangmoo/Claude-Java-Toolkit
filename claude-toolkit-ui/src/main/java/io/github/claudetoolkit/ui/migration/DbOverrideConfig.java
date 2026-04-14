package io.github.claudetoolkit.ui.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.File;
import java.io.FileInputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * DB 런타임 오버라이드 로더 (v4.2.2).
 *
 * <p>사용자 홈/작업 디렉토리의 {@code data/db-override.properties} 파일이 존재하면
 * 해당 파일의 DB 연결 정보로 {@code spring.datasource.*} 및 JPA dialect 를 덮어씁니다.
 * 자동 이관 완료 후 "대상 DB 로 전환" 기능이 이 파일을 생성합니다.
 * "H2 로 복귀" 기능은 이 파일을 삭제합니다.
 *
 * <p>이 클래스는 Spring Boot 자동 구성 이전에 실행되어야 하므로
 * {@code META-INF/spring.factories} 에 등록합니다.
 */
public class DbOverrideConfig implements EnvironmentPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(DbOverrideConfig.class);

    public static final String OVERRIDE_FILE = "data/db-override.properties";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment env, SpringApplication app) {
        File f = new File(OVERRIDE_FILE);
        if (!f.exists() || !f.isFile()) return;

        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(f)) {
            p.load(in);
        } catch (Exception e) {
            log.warn("[DbOverride] 파일 로드 실패: {}", e.getMessage());
            return;
        }

        String url      = p.getProperty("url");
        String driver   = p.getProperty("driver");
        String user     = p.getProperty("username");
        String pass     = p.getProperty("password");
        String dialect  = p.getProperty("dialect");

        if (url == null || driver == null) {
            log.warn("[DbOverride] 파일이 불완전 — 무시");
            return;
        }

        Map<String, Object> overrides = new LinkedHashMap<>();
        overrides.put("spring.datasource.url",               url);
        overrides.put("spring.datasource.driver-class-name", driver);
        overrides.put("spring.datasource.username",          user == null ? "" : user);
        overrides.put("spring.datasource.password",          pass == null ? "" : pass);
        overrides.put("spring.datasource.hikari.maximum-pool-size", "10");
        overrides.put("spring.datasource.hikari.minimum-idle",      "2");
        overrides.put("spring.datasource.hikari.connection-timeout","30000");
        if (dialect != null && !dialect.isEmpty()) {
            overrides.put("spring.jpa.properties.hibernate.dialect", dialect);
        }
        // H2 콘솔/플랫폼 비활성
        overrides.put("spring.h2.console.enabled", "false");

        // 최우선 적용
        env.getPropertySources().addFirst(new MapPropertySource("dbOverride", overrides));
        log.info("[DbOverride] 런타임 DB 오버라이드 활성: {} ({})", url, driver);
    }
}
