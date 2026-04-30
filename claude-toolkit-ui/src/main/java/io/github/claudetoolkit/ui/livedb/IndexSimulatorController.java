package io.github.claudetoolkit.ui.livedb;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.7.x — #G3 Live DB Phase 4: 인덱스 시뮬레이션 REST 엔드포인트.
 *
 * <p>{@code POST /api/v1/livedb/simulate-index}
 *
 * <p>Body (JSON):
 * <pre>{@code
 * {
 *   "userSql":     "SELECT * FROM T_ORDER WHERE STATUS='Y' AND ORDER_DATE >= ...",
 *   "indexDefs":   ["CREATE INDEX IDX1 ON T_ORDER (ORDER_DATE, STATUS)"],
 *   "dbProfileId": 42
 * }
 * }</pre>
 *
 * <p>Response (성공):
 * <pre>{@code
 * {
 *   "success": true,
 *   "data": {
 *     "beforeCost": 4521,
 *     "afterCost":  892,
 *     "improvementPercent": 80.3,
 *     "beforePlanText": "...",
 *     "afterPlanText":  "...",
 *     "warnings": []
 *   }
 * }
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/livedb")
public class IndexSimulatorController {

    private static final Logger log = LoggerFactory.getLogger(IndexSimulatorController.class);

    private final IndexSimulatorService service;

    public IndexSimulatorController(IndexSimulatorService service) {
        this.service = service;
    }

    /**
     * 인덱스 시뮬레이션 실행. 본문이 잘못됐거나 (read-only SQL 아님 / 인덱스 5개 초과)
     * SecurityException 으로 거부되면 400 반환.
     */
    @PostMapping("/simulate-index")
    public ResponseEntity<Map<String, Object>> simulate(@RequestBody SimulateRequest req) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();

        if (req == null || req.userSql == null || req.userSql.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "userSql 필수");
            return ResponseEntity.badRequest().body(resp);
        }
        if (req.indexDefs == null || req.indexDefs.isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "indexDefs 최소 1개 필요");
            return ResponseEntity.badRequest().body(resp);
        }
        if (req.dbProfileId == null) {
            resp.put("success", false);
            resp.put("error",   "dbProfileId 필수");
            return ResponseEntity.badRequest().body(resp);
        }

        try {
            IndexSimulationResult result = service.simulate(
                    req.userSql, req.indexDefs, req.dbProfileId);
            if (result == null) {
                resp.put("success", false);
                resp.put("error",   "시뮬레이션 비활성 또는 프로필 비활성");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(resp);
            }
            resp.put("success", true);
            resp.put("data",    toResponseMap(result));
            return ResponseEntity.ok(resp);

        } catch (SecurityException e) {
            // SqlClassifier 또는 IndexSimulator 의 안전 검증 실패
            resp.put("success", false);
            resp.put("error",   e.getMessage());
            return ResponseEntity.badRequest().body(resp);
        } catch (Exception e) {
            log.warn("[IndexSimulator] 시뮬레이션 실패: {}", e.getMessage(), e);
            resp.put("success", false);
            resp.put("error",   "시뮬레이션 실패: " + e.getMessage());
            return ResponseEntity.internalServerError().body(resp);
        }
    }

    private static Map<String, Object> toResponseMap(IndexSimulationResult r) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("userSql",            r.getUserSql());
        m.put("simulatedIndexes",   r.getSimulatedIndexes());
        m.put("beforeCost",         r.getBeforeCost());
        m.put("afterCost",          r.getAfterCost());
        m.put("beforePlanText",     r.getBeforePlanText());
        m.put("afterPlanText",      r.getAfterPlanText());
        m.put("improvementPercent", r.getImprovementPercent());
        m.put("hasComparison",      r.hasComparison());
        m.put("warnings",           r.getWarnings());
        m.put("simulatedAtMillis",  r.getSimulatedAtMillis());
        return m;
    }

    /** 요청 body — Jackson 자동 매핑 */
    public static class SimulateRequest {
        public String       userSql;
        public List<String> indexDefs;
        public Long         dbProfileId;
    }
}
