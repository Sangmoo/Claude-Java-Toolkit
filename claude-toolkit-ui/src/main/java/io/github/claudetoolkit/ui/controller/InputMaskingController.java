package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.masking.SensitiveMaskingService;
import io.github.claudetoolkit.ui.masking.SensitiveMaskingService.MaskingResult;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 양방향 민감정보 마스킹 컨트롤러 (/input-masking)
 *
 * <ul>
 *   <li>GET  /input-masking        — UI 페이지</li>
 *   <li>POST /input-masking/mask   — 마스킹 적용, JSON 응답</li>
 *   <li>POST /input-masking/unmask — 토큰 복원, JSON 응답</li>
 * </ul>
 */
@Controller
@RequestMapping("/input-masking")
public class InputMaskingController {

    private final SensitiveMaskingService maskingService;

    public InputMaskingController(SensitiveMaskingService maskingService) {
        this.maskingService = maskingService;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("patterns", maskingService.getSupportedPatterns());
        return "input-masking/index";
    }

    /**
     * 마스킹 적용 API.
     * <pre>
     * Request: text=원본텍스트 &amp; types=SSN &amp; types=EMAIL ...
     * Response: { maskedText, tokenMap:{토큰:원본}, countByType:{유형:수}, totalMasked }
     * </pre>
     */
    @PostMapping("/mask")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> mask(
            @RequestParam("text")                          String text,
            @RequestParam(value = "types", required = false) List<String> types) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            MaskingResult result = maskingService.mask(text, types);

            // tokenMap → JS 배열 형태로 직렬화 (순서 보장)
            List<Map<String, String>> tokenList = new ArrayList<Map<String, String>>();
            for (Map.Entry<String, String> e : result.getTokenMap().entrySet()) {
                Map<String, String> row = new LinkedHashMap<String, String>();
                row.put("token",    e.getKey());
                row.put("original", e.getValue());
                row.put("type",     extractType(e.getKey()));
                tokenList.add(row);
            }

            resp.put("success",      true);
            resp.put("maskedText",   result.getMaskedText());
            resp.put("tokenList",    tokenList);
            resp.put("countByType",  result.getCountByType());
            resp.put("totalMasked",  result.getTotalMasked());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /**
     * 토큰 복원 API.
     * <pre>
     * Request: maskedText=텍스트 &amp; tokenJson={"{{MASK_EMAIL_1}}":"user@example.com",...}
     * Response: { restoredText, restoredCount }
     * </pre>
     */
    @PostMapping("/unmask")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> unmask(
            @RequestParam("maskedText")  String maskedText,
            @RequestParam("tokenJson")   String tokenJson) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            Map<String, String> tokenMap = parseTokenJson(tokenJson);
            String restored = maskingService.unmask(maskedText, tokenMap);

            int count = 0;
            for (String token : tokenMap.keySet()) {
                if (maskedText.contains(token)) count++;
            }

            resp.put("success",       true);
            resp.put("restoredText",  restored);
            resp.put("restoredCount", count);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 유틸 ─────────────────────────────────────────────────────────────────

    /** "{{MASK_EMAIL_1}}" → "EMAIL" */
    private String extractType(String token) {
        // token = {{MASK_TYPE_N}}
        if (token.startsWith("{{MASK_") && token.endsWith("}}")) {
            String inner = token.substring(7, token.length() - 2); // TYPE_N
            int lastUnd = inner.lastIndexOf('_');
            if (lastUnd > 0) return inner.substring(0, lastUnd);
        }
        return "UNKNOWN";
    }

    /**
     * 단순 JSON 파싱: {"key":"value",...}
     * spring-security-crypto 외 JSON 라이브러리 없이 처리.
     */
    private Map<String, String> parseTokenJson(String json) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        if (json == null || json.trim().isEmpty()) return map;
        String trimmed = json.trim();
        if (trimmed.startsWith("{")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("}"))   trimmed = trimmed.substring(0, trimmed.length() - 1);

        // "key":"value" 쌍 파싱 (간단한 경우만 지원 — 값에 중괄호 없음)
        // 형식: "{{MASK_TYPE_N}}":"original_value"
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
            "\"(\\{\\{[^\"]+\\}\\})\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        java.util.regex.Matcher m = p.matcher(trimmed);
        while (m.find()) {
            String key = m.group(1);
            String val = m.group(2)
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n",  "\n")
                .replace("\\r",  "\r")
                .replace("\\t",  "\t");
            map.put(key, val);
        }
        return map;
    }
}
