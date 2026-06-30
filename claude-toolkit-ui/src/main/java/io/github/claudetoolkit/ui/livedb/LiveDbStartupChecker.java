package io.github.claudetoolkit.ui.livedb;

import io.github.claudetoolkit.ui.dbprofile.DbProfile;
import io.github.claudetoolkit.ui.dbprofile.DbProfileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * v4.7.x — #G3 Live DB Phase 0 보강:
 *
 * <p>글로벌 feature flag ({@code toolkit.livedb.enabled}) 가 ON 인데 "Live DB 분석"
 * 토글이 켜진 DbProfile 이 한 건도 없으면, 분석 페이지의 dropdown 이 비어 사용자는
 * "왜 Live DB 가 안 뜨지?" 로 혼란을 겪는다. 이 상황을 시작 시 한 번 WARN 으로 알린다.
 *
 * <p>글로벌이 OFF 이면 의도적으로 꺼둔 것이므로 이 체크는 건너뛴다.
 *
 * <p>{@link ApplicationReadyEvent} 시점에 1회 실행 — 모든 빈 초기화 + DB 마이그레이션
 * 완료 후라 {@code DbProfileService.findActiveLiveAnalysisProfiles()} 가 정확한 결과를
 * 반환한다.
 */
@Component
public class LiveDbStartupChecker {

    private static final Logger log = LoggerFactory.getLogger(LiveDbStartupChecker.class);

    private final LiveDbConfig     liveDbConfig;
    private final DbProfileService dbProfileService;

    public LiveDbStartupChecker(LiveDbConfig liveDbConfig, DbProfileService dbProfileService) {
        this.liveDbConfig     = liveDbConfig;
        this.dbProfileService = dbProfileService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void checkActiveProfiles() {
        if (!liveDbConfig.isEnabled()) {
            log.info("[LiveDb] 글로벌 비활성 (toolkit.livedb.enabled=false) — 활성 프로필 검사 건너뜀");
            return;
        }
        List<DbProfile> active = dbProfileService.findActiveLiveAnalysisProfiles();
        if (active.isEmpty()) {
            log.warn("[LiveDb] ⚠️ 글로벌 활성이지만 'Live DB 분석' 토글이 켜진 DbProfile 이 한 건도 없습니다. "
                  + "분석 페이지에서 Live DB 컨텍스트가 첨부되지 않습니다. "
                  + "/db-profiles 페이지에서 최소 1개 프로필의 'Live DB 분석' 토글을 켜거나, "
                  + "쓰지 않을 거면 TOOLKIT_LIVEDB_ENABLED=false 로 글로벌 flag 를 끄세요.");
        } else {
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < active.size(); i++) {
                if (i > 0) names.append(", ");
                names.append(active.get(i).getName());
            }
            log.info("[LiveDb] 활성 프로필 {} 건 [{}] — Live DB 채널 ready", active.size(), names);
        }
    }
}
