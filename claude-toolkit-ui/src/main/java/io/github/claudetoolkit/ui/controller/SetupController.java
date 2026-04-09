package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import io.github.claudetoolkit.ui.config.SettingsPersistenceService;
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
 *   <li>Claude API 키 설정 + 연결 테스트 + 저장</li>
 *   <li>DB 설정 + 연결 테스트 + 저장</li>
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
    private final ClaudeProperties claudeProperties;
    private final ToolkitSettings settings;
    private final SettingsPersistenceService persistenceService;

    public SetupController(ClaudeClient claudeClient, ClaudeProperties claudeProperties,
                           ToolkitSettings settings, SettingsPersistenceService persistenceService) {
        this.claudeClient       = claudeClient;
        this.claudeProperties   = claudeProperties;
        this.settings           = settings;
        this.persistenceService = persistenceService;
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

    /** API 키 저장 + 연결 테스트 */
    @PostMapping("/save-api-key")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveApiKey(@RequestParam String apiKey) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            // 런타임 적용
            claudeProperties.setApiKey(apiKey.trim());
            // 파일 영속화
            persistenceService.save();
            // 연결 테스트
            String result = claudeClient.chat("Say 'OK' in one word.");
            resp.put("success", true);
            resp.put("model", claudeClient.getEffectiveModel());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** API 키 연결 테스트 (저장 없이) */
    @PostMapping("/test-api")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> testApi() {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            String result = claudeClient.chat("Say 'OK' in one word.");
            resp.put("success", true);
            resp.put("model", claudeClient.getEffectiveModel());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** Oracle DB 설정 저장 + 연결 테스트 */
    @PostMapping("/save-db")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> saveDb(
            @RequestParam String dbUrl,
            @RequestParam String dbUsername,
            @RequestParam String dbPassword) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            // 연결 테스트 먼저
            Connection conn = DriverManager.getConnection(dbUrl, dbUsername, dbPassword);
            Statement stmt = conn.createStatement();
            stmt.execute("SELECT 1 FROM DUAL");
            stmt.close();
            conn.close();
            // 런타임 적용 + 영속화
            settings.getDb().setUrl(dbUrl.trim());
            settings.getDb().setUsername(dbUsername.trim());
            settings.getDb().setPassword(dbPassword.trim());
            persistenceService.save();
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** DB 연결 테스트 (저장 없이) */
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
