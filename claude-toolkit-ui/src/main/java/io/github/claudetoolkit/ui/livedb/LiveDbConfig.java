package io.github.claudetoolkit.ui.livedb;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * v4.7.x — #G3 Live DB Phase 0: 전역 feature flag + 안전 기본값.
 *
 * <p>application.yml 의 <code>toolkit.livedb</code> 섹션에 매핑.
 *
 * <p><b>Default OFF</b>: {@link #enabled} 기본 false — 운영 환경에서 자동
 * 활성화 사고 방지. ADMIN 이 명시적으로 yml 또는 환경변수 (TOOLKIT_LIVEDB_ENABLED=true)
 * 로 활성화해야만 작동.
 */
@Configuration
@ConfigurationProperties(prefix = "toolkit.livedb")
public class LiveDbConfig {

    /**
     * Live DB 채널 전체 ON/OFF. 기본 false — 사고 시 즉시 비활성화 가능한 kill switch.
     * 이 값이 false 면 {@link ReadOnlyJdbcTemplate} 가 모든 호출을 거부.
     */
    private boolean enabled = false;

    /**
     * 모든 Live DB 쿼리에 강제되는 statement timeout (초).
     * 사용자가 무거운 쿼리로 운영 DB 를 부하 줄 수 없게 강제.
     * DbProfile.liveQueryTimeoutSeconds 가 설정되어 있으면 그쪽이 우선.
     */
    private int defaultTimeoutSeconds = 30;

    /**
     * Live DB 쿼리가 fetch 할 수 있는 최대 row 수 — 메모리 보호.
     * EXPLAIN/DESC 는 자체적으로 작은 결과만 반환하므로 영향 없음. 통계 메타 조회용.
     */
    private int maxRows = 1000;

    /**
     * 사용자 + 프로필 조합당 분당 최대 호출 수 (Phase 5 에서 활성화).
     * 0 또는 음수면 제한 없음 (개발용). 기본 10/min.
     */
    private int maxCallsPerMinute = 10;

    public boolean isEnabled()                            { return enabled; }
    public void    setEnabled(boolean enabled)            { this.enabled = enabled; }
    public int     getDefaultTimeoutSeconds()             { return defaultTimeoutSeconds; }
    public void    setDefaultTimeoutSeconds(int s)        { this.defaultTimeoutSeconds = s; }
    public int     getMaxRows()                           { return maxRows; }
    public void    setMaxRows(int maxRows)                { this.maxRows = maxRows; }
    public int     getMaxCallsPerMinute()                 { return maxCallsPerMinute; }
    public void    setMaxCallsPerMinute(int n)            { this.maxCallsPerMinute = n; }
}
