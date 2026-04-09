package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.security.SecuritySettings;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 설치 마법사 컨트롤러 (/setup).
 *
 * <p>4단계 위저드:
 * <ol>
 *   <li>Claude API 키 설정 + 연결 테스트</li>
 *   <li>DB 설정 (H2 기본, MySQL/PostgreSQL 선택)</li>
 *   <li>이메일 설정 (선택)</li>
 *   <li>관리자 계정 확인</li>
 * </ol>
 *
 * <p>"나중에 설정하기" 버튼으로 스킵 가능 → Settings에서 개별 설정.
 */
@Controller
@RequestMapping("/setup")
public class SetupController {

    private final ClaudeClient claudeClient;
    private final ToolkitSettings settings;

    public SetupController(ClaudeClient claudeClient, ToolkitSettings settings) {
        this.claudeClient = claudeClient;
        this.settings     = settings;
    }

    @GetMapping
    public String setupPage(Model model) {
        // 이미 설치 완료된 경우 메인으로
        if (isSetupCompleted()) {
            return "redirect:/";
        }
        model.addAttribute("apiKeySet", claudeClient.getApiKey() != null && !claudeClient.getApiKey().isEmpty());
        return "setup";
    }

    /** API 키 연결 테스트 */
    @PostMapping("/test-api")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testApi() {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            String result = claudeClient.chat("Say 'OK' in one word.");
            resp.put("success", true);
            resp.put("model", claudeClient.getEffectiveModel());
            resp.put("response", result != null ? result.trim() : "");
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** DB 연결 테스트 */
    @PostMapping("/test-db")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testDb(
            @RequestParam String dbUrl,
            @RequestParam String dbUsername,
            @RequestParam String dbPassword) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            Statement stmt = conn.createStatement();
            stmt.execute("SELECT 1");
            stmt.close();
            conn.close();
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 설치 완료 처리 */
    @PostMapping("/complete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> complete() {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            SecuritySettings ss = SecuritySettings.load();
            ss.setSetupCompleted(true);
            ss.save();
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** "나중에 설정하기" — 설치 완료 마킹하고 메인으로 */
    @PostMapping("/skip")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> skip() {
        return complete(); // 동일하게 setupCompleted=true
    }

    private boolean isSetupCompleted() {
        try {
            return SecuritySettings.load().isSetupCompleted();
        } catch (Exception e) {
            return false;
        }
    }
}
