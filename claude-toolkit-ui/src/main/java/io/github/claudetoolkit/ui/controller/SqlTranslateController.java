package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.ui.translate.SqlTranslateService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * SQL 번역 컨트롤러 — 이종 DB 쿼리 마이그레이션 (/sql-translate)
 *
 * <p>두 단계 흐름:
 * <ol>
 *   <li>POST /sql-translate/init — 입력값 저장, streamId 반환</li>
 *   <li>GET  /stream/{id}        — SSE 스트리밍 (SseStreamController 공용)</li>
 * </ol>
 */
@Controller
@RequestMapping("/sql-translate")
public class SqlTranslateController {

    private final SqlTranslateService  translateService;
    private final SseStreamController  sseController;
    private final ReviewHistoryService historyService;

    public SqlTranslateController(SqlTranslateService translateService,
                                  SseStreamController sseController,
                                  ReviewHistoryService historyService) {
        this.translateService = translateService;
        this.sseController    = sseController;
        this.historyService   = historyService;
    }

    /** SQL 번역 페이지 */
    @GetMapping
    public String index() {
        return "sql-translate/index";
    }

    /**
     * 스트림 등록 — 소스 DB, 대상 DB, SQL을 저장하고 streamId 반환.
     * sourceType 필드에 "sourceDb|targetDb" 형태로 저장해 SseStreamController에 전달.
     */
    @PostMapping("/init")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> init(
            @RequestParam("sql")      String sql,
            @RequestParam("sourceDb") String sourceDb,
            @RequestParam("targetDb") String targetDb) {

        Map<String, Object> resp = new HashMap<String, Object>();
        if (sql == null || sql.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error", "SQL을 입력해주세요.");
            return ResponseEntity.badRequest().body(resp);
        }
        // input2 = sourceDb, sourceType = targetDb 로 재활용
        String streamId = sseController.registerStream("sql_translate", sql.trim(), sourceDb, targetDb);
        resp.put("success", true);
        resp.put("streamId", streamId);
        return ResponseEntity.ok(resp);
    }

    /**
     * 번역 완료 후 이력 저장 (비스트리밍 POST 방식으로 UI에서 호출).
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(
            @RequestParam("sql")      String sql,
            @RequestParam("result")   String result,
            @RequestParam("sourceDb") String sourceDb,
            @RequestParam("targetDb") String targetDb) {

        Map<String, Object> resp = new HashMap<String, Object>();
        try {
            String combined = "[" + sourceDb + " → " + targetDb + "]\n" + sql;
            historyService.save("SQL_TRANSLATE", combined, result);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
