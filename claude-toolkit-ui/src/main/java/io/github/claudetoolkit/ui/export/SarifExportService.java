package io.github.claudetoolkit.ui.export;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v4.3.0 — SARIF 2.1.0 (Static Analysis Results Interchange Format) 변환 서비스.
 *
 * <p>{@link ReviewHistory} 의 분석 결과를 SARIF JSON 으로 변환하여
 * VS Code SARIF Viewer / JetBrains Qodana / GitHub Code Scanning 에서
 * 활용할 수 있도록 합니다.
 *
 * <p>SARIF 스펙: https://docs.oasis-open.org/sarif/sarif/v2.1.0/sarif-v2.1.0.html
 *
 * <p>변환 규칙:
 * <ul>
 *   <li>출력 텍스트의 {@code [SEVERITY: HIGH|MEDIUM|LOW]} 마커를 SARIF level 로 매핑
 *       (HIGH → error, MEDIUM → warning, LOW → note)</li>
 *   <li>severity 마커가 없으면 출력 본문 전체를 단일 note 로 포함</li>
 *   <li>분석 유형(type)을 ruleId 로, 분석 제목을 ruleName 으로 사용</li>
 *   <li>입력 파일명/경로를 알 수 없으므로 인공 URI {@code analysis-input.txt} 사용</li>
 * </ul>
 */
@Service
public class SarifExportService {

    private static final Logger log = LoggerFactory.getLogger(SarifExportService.class);

    private static final String SARIF_VERSION = "2.1.0";
    private static final String SARIF_SCHEMA  = "https://json.schemastore.org/sarif-2.1.0.json";
    private static final String TOOL_NAME     = "Claude Java Toolkit";
    private static final String TOOL_VERSION  = "4.3.0";
    private static final String TOOL_URI      = "https://github.com/Sangmoo/Claude-Java-Toolkit";

    /** [SEVERITY: HIGH/MEDIUM/LOW] - <message> 패턴 (한 줄 단위) */
    private static final Pattern SEVERITY_LINE = Pattern.compile(
            "\\[SEVERITY:\\s*(HIGH|MEDIUM|LOW)\\]\\s*[-:–—]?\\s*(.+)",
            Pattern.CASE_INSENSITIVE);

    /** 단순 [SEVERITY: HIGH] 마커만 있는 경우 */
    private static final Pattern SEVERITY_MARKER = Pattern.compile(
            "\\[SEVERITY:\\s*(HIGH|MEDIUM|LOW)\\]",
            Pattern.CASE_INSENSITIVE);

    /**
     * 단일 ReviewHistory 엔트리를 SARIF Map (Jackson 직렬화 가능) 으로 변환.
     */
    public Map<String, Object> toSarif(ReviewHistory h) {
        if (h == null) {
            throw new IllegalArgumentException("ReviewHistory 가 null 입니다.");
        }

        String ruleId = h.getType() != null ? h.getType() : "ANALYSIS";
        String ruleName = h.getTypeLabel();
        String output = h.getOutputContent() != null ? h.getOutputContent() : "";

        // ── 1. results: severity 마커 라인을 개별 result 로 추출 ──────────
        List<Map<String, Object>> results = extractResults(output, ruleId);
        if (results.isEmpty()) {
            // 마커가 없으면 분석 결과 전체를 단일 note 로 포함
            results.add(buildResult(ruleId, "note", truncate(output, 4000)));
        }

        // ── 2. rules: 사용된 ruleId 들에 대한 메타데이터 ────────────────
        Map<String, Object> rule = new LinkedHashMap<String, Object>();
        rule.put("id",   ruleId);
        rule.put("name", ruleName);
        Map<String, Object> shortDesc = new LinkedHashMap<String, Object>();
        shortDesc.put("text", ruleName + " (Claude Java Toolkit)");
        rule.put("shortDescription", shortDesc);
        Map<String, Object> fullDesc = new LinkedHashMap<String, Object>();
        fullDesc.put("text", "Claude AI 기반 " + ruleName + " 분석 결과 — id=" + h.getId());
        rule.put("fullDescription", fullDesc);
        rule.put("helpUri", TOOL_URI);

        // ── 3. driver / tool ─────────────────────────────────────────────
        Map<String, Object> driver = new LinkedHashMap<String, Object>();
        driver.put("name",            TOOL_NAME);
        driver.put("version",         TOOL_VERSION);
        driver.put("informationUri",  TOOL_URI);
        driver.put("rules",           new Object[]{ rule });

        Map<String, Object> tool = new LinkedHashMap<String, Object>();
        tool.put("driver", driver);

        // ── 4. run ───────────────────────────────────────────────────────
        Map<String, Object> run = new LinkedHashMap<String, Object>();
        run.put("tool",    tool);
        run.put("results", results);
        // properties — 분석 메타정보
        Map<String, Object> props = new LinkedHashMap<String, Object>();
        props.put("title",      h.getTitle());
        props.put("createdAt",  h.getCreatedAt() != null ? h.getCreatedAt().toString() : null);
        props.put("username",   h.getUsername());
        props.put("historyId",  h.getId());
        if (h.getInputTokens() != null)  props.put("inputTokens",  h.getInputTokens());
        if (h.getOutputTokens() != null) props.put("outputTokens", h.getOutputTokens());
        run.put("properties", props);

        // ── 5. 최상위 SARIF 객체 ──────────────────────────────────────────
        Map<String, Object> sarif = new LinkedHashMap<String, Object>();
        sarif.put("$schema", SARIF_SCHEMA);
        sarif.put("version", SARIF_VERSION);
        sarif.put("runs",    new Object[]{ run });

        log.debug("SARIF 변환 완료: historyId={}, type={}, results={}", h.getId(), ruleId, results.size());
        return sarif;
    }

    /** 출력 본문에서 [SEVERITY: X] 라인을 찾아 SARIF result 배열 생성 */
    private List<Map<String, Object>> extractResults(String output, String ruleId) {
        List<Map<String, Object>> results = new ArrayList<Map<String, Object>>();
        if (output == null || output.isEmpty()) return results;

        // 줄 단위로 스캔 — 각 라인의 [SEVERITY: X] - 메시지 패턴 매칭
        String[] lines = output.split("\\R");
        Set<String> seen = new LinkedHashSet<String>(); // 중복 제거

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            Matcher m = SEVERITY_LINE.matcher(line);
            if (m.find()) {
                String sev = m.group(1).toUpperCase();
                String msg = m.group(2).trim();
                if (msg.isEmpty()) {
                    // 같은 줄에 메시지가 없으면 다음 비어있지 않은 줄을 메시지로
                    msg = nextNonEmpty(lines, i + 1);
                }
                String key = sev + "|" + msg;
                if (seen.add(key)) {
                    results.add(buildResult(ruleId, mapLevel(sev), truncate(msg, 2000)));
                }
            } else {
                // 라인에 SEVERITY 마커만 있고 메시지가 없는 케이스
                Matcher mm = SEVERITY_MARKER.matcher(line);
                if (mm.find() && !SEVERITY_LINE.matcher(line).find()) {
                    String sev = mm.group(1).toUpperCase();
                    String msg = nextNonEmpty(lines, i + 1);
                    if (!msg.isEmpty()) {
                        String key = sev + "|" + msg;
                        if (seen.add(key)) {
                            results.add(buildResult(ruleId, mapLevel(sev), truncate(msg, 2000)));
                        }
                    }
                }
            }
        }
        return results;
    }

    private String nextNonEmpty(String[] lines, int from) {
        for (int j = from; j < Math.min(lines.length, from + 5); j++) {
            String s = lines[j].trim();
            if (!s.isEmpty()) return s;
        }
        return "";
    }

    private Map<String, Object> buildResult(String ruleId, String level, String text) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("ruleId", ruleId);
        result.put("level",  level);
        Map<String, Object> message = new LinkedHashMap<String, Object>();
        message.put("text", text);
        result.put("message", message);

        // location — 입력 파일명을 모르므로 분석 입력에 대한 인공 URI
        Map<String, Object> artifactLoc = new LinkedHashMap<String, Object>();
        artifactLoc.put("uri", "analysis-input.txt");
        Map<String, Object> physicalLoc = new LinkedHashMap<String, Object>();
        physicalLoc.put("artifactLocation", artifactLoc);
        Map<String, Object> location = new LinkedHashMap<String, Object>();
        location.put("physicalLocation", physicalLoc);
        result.put("locations", new Object[]{ location });
        return result;
    }

    /** SARIF level 은 none / note / warning / error 중 하나 */
    private String mapLevel(String severity) {
        if ("HIGH".equals(severity))   return "error";
        if ("MEDIUM".equals(severity)) return "warning";
        if ("LOW".equals(severity))    return "note";
        return "none";
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + " …(생략)";
    }
}
