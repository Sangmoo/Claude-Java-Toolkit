package io.github.claudetoolkit.ui.config;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;

/**
 * 앱 시작 시 warmup 진행률을 반환하는 공개 엔드포인트.
 * SecurityConfig 에서 permitAll 처리 — 로그인 전에도 프론트가 진행률 배너를 표시할 수 있도록.
 */
@RestController
@RequestMapping("/api/v1/status")
public class WarmupProgressController {

    private static final List<String> STAGE_ORDER = Arrays.asList(
            "STARTING", "LOADING_SETTINGS", "TESTING_DATASOURCE", "TESTING_EXTERNAL_DB",
            "CHECKING_PROJECT_PATH", "VERIFYING_HARNESS_CACHE", "VERIFYING_FLOW_INDEXERS", "READY"
    );

    private static final Map<String, String> STAGE_LABELS = new LinkedHashMap<String, String>();
    static {
        STAGE_LABELS.put("STARTING",               "시작 준비중...");
        STAGE_LABELS.put("LOADING_SETTINGS",        "설정 파일 로드중...");
        STAGE_LABELS.put("TESTING_DATASOURCE",      "데이터베이스 연결 확인중...");
        STAGE_LABELS.put("TESTING_EXTERNAL_DB",     "외부 Oracle DB 연결 확인중...");
        STAGE_LABELS.put("CHECKING_PROJECT_PATH",   "프로젝트 경로 확인중...");
        STAGE_LABELS.put("VERIFYING_HARNESS_CACHE", "소스 분석 캐시 초기화중...");
        STAGE_LABELS.put("VERIFYING_FLOW_INDEXERS", "Flow 인덱서 초기화중...");
        STAGE_LABELS.put("READY",                   "준비 완료");
        STAGE_LABELS.put("FAILED_DATASOURCE",       "DB 연결 실패");
    }

    private final StartupReadiness.StartupWarmup warmup;

    public WarmupProgressController(StartupReadiness.StartupWarmup warmup) {
        this.warmup = warmup;
    }

    @GetMapping("/progress")
    public Map<String, Object> progress() {
        String stage = warmup.getStage();
        int idx = STAGE_ORDER.indexOf(stage);
        int total = STAGE_ORDER.size() - 1;
        int pct = warmup.isReady() ? 100 : (idx < 0 ? 0 : (int) (100.0 * idx / total));

        Map<String, Object> map = new LinkedHashMap<String, Object>();
        map.put("ready", warmup.isReady());
        map.put("stage", stage);
        map.put("label", STAGE_LABELS.getOrDefault(stage, stage));
        map.put("pct",   pct);
        String err = warmup.getLastErrorOrNone();
        if (!warmup.isReady() && err != null && !"(없음)".equals(err)) {
            map.put("error", err);
        }
        return map;
    }
}
