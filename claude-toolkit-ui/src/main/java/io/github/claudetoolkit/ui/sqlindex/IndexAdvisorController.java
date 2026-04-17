package io.github.claudetoolkit.ui.sqlindex;

import io.github.claudetoolkit.ui.api.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * v4.3.0 — SQL 인덱스 임팩트 시뮬레이션 REST API.
 *
 * <ul>
 *   <li>{@code POST /api/v1/sql/index-advisor} — 입력 SQL 분석 + 기존/신규 인덱스 추천</li>
 * </ul>
 *
 * <p>요청 본문:
 * <pre>{
 *   "sql": "SELECT * FROM ORDERS o JOIN USERS u ON o.user_id = u.id WHERE u.email = ? AND o.status = 'NEW'",
 *   "dbProfile": "current"
 * }</pre>
 */
@RestController
@RequestMapping("/api/v1/sql")
public class IndexAdvisorController {

    private static final Logger log = LoggerFactory.getLogger(IndexAdvisorController.class);

    private final IndexAdvisorService advisorService;

    public IndexAdvisorController(IndexAdvisorService advisorService) {
        this.advisorService = advisorService;
    }

    @PostMapping("/index-advisor")
    public ResponseEntity<ApiResponse<Map<String, Object>>> indexAdvisor(
            @RequestBody Map<String, String> body) {
        String sql = body.get("sql");
        String dbProfile = body.getOrDefault("dbProfile", "current");

        if (sql == null || sql.trim().isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("sql 필드는 필수입니다."));
        }

        try {
            Map<String, Object> result = advisorService.analyze(sql, dbProfile);
            return ResponseEntity.ok(ApiResponse.ok(result));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.warn("인덱스 어드바이저 실패: sql 길이={}", sql.length(), e);
            return ResponseEntity.status(500)
                    .body(ApiResponse.error("인덱스 시뮬레이션 실패: " + e.getMessage()));
        }
    }
}
