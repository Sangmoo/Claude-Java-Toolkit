package io.github.claudetoolkit.ui.masking;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.*;

/**
 * 양방향 민감정보 마스킹 서비스.
 *
 * <p><b>흐름:</b>
 * <ol>
 *   <li>{@link #mask(String, List)} — 입력 텍스트에서 민감정보를 찾아 토큰으로 교체</li>
 *   <li>마스킹된 텍스트를 Claude AI에 전송</li>
 *   <li>{@link #unmask(String, Map)} — AI 응답(또는 마스킹된 텍스트)의 토큰을 원본으로 복원</li>
 * </ol>
 *
 * <p>토큰 형식: {@code {{MASK_유형_N}}} — 예: {@code {{MASK_EMAIL_1}}}
 */
@Service
public class SensitiveMaskingService {

    private static final Logger log = LoggerFactory.getLogger(SensitiveMaskingService.class);

    // ── 지원 패턴 유형 ─────────────────────────────────────────────────────────

    public enum PatternType {
        SSN        ("주민등록번호",  "\\b\\d{6}-[1-4]\\d{6}\\b"),
        CREDIT_CARD("신용카드번호",  "\\b\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}[\\s\\-]?\\d{4}\\b"),
        EMAIL      ("이메일",       "[a-zA-Z0-9._%+\\-]+@[a-zA-Z0-9.\\-]+\\.[a-zA-Z]{2,}"),
        PHONE_KR   ("전화번호",     "\\b0[1-9]\\d?[\\-.]\\d{3,4}[\\-.]\\d{4}\\b"),
        IP_ADDR    ("IP 주소",      "\\b(?:(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(?:25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\b"),
        PASSWORD   ("비밀번호 패턴", "(?i)(?:password|passwd|pwd)\\s*[=:]\\s*['\"]?([^\\s'\"\\n,;]+)['\"]?"),
        API_KEY    ("API 키/토큰",  "(?i)(?:api[_\\-]?key|apikey|access[_\\-]?token|secret[_\\-]?key)\\s*[=:\\s]\\s*['\"]?([A-Za-z0-9+/=_\\-]{16,})['\"]?"),
        ACCOUNT_NO ("계좌번호",     "\\b\\d{3}[-\\s]?\\d{6}[-\\s]?\\d{2,3}\\b");

        public final String label;
        public final String regex;

        PatternType(String label, String regex) {
            this.label = label;
            this.regex = regex;
        }
    }

    // ── 마스킹 ────────────────────────────────────────────────────────────────

    /**
     * 텍스트에서 민감정보를 탐지하여 토큰으로 교체합니다.
     *
     * @param text          원본 텍스트
     * @param enabledTypes  활성화할 패턴 유형 목록 (null 또는 empty = 전체 적용)
     * @return 마스킹 결과 (마스킹된 텍스트 + 토큰 맵)
     */
    public MaskingResult mask(String text, List<String> enabledTypes) {
        if (text == null || text.isEmpty()) {
            return new MaskingResult(text, new LinkedHashMap<String, String>(),
                                     new LinkedHashMap<String, Integer>());
        }

        // 토큰 순번 관리
        Map<String, Integer> counters    = new LinkedHashMap<String, Integer>();
        Map<String, String>  tokenMap    = new LinkedHashMap<String, String>();
        Map<String, Integer> countByType = new LinkedHashMap<String, Integer>();

        // 마스킹은 위치가 겹칠 수 있으므로 탐지된 범위를 수집 후 교체
        List<MatchSpan> spans = new ArrayList<MatchSpan>();

        PatternType[] types = PatternType.values();
        for (PatternType pt : types) {
            if (!isEnabled(pt.name(), enabledTypes)) continue;

            try {
                Pattern p = Pattern.compile(pt.regex);
                Matcher m = p.matcher(text);
                while (m.find()) {
                    // PASSWORD / API_KEY 패턴은 group(1) (값 부분만)
                    int start, end;
                    String original;
                    if ((pt == PatternType.PASSWORD || pt == PatternType.API_KEY)
                            && m.groupCount() >= 1 && m.group(1) != null) {
                        start    = m.start(1);
                        end      = m.end(1);
                        original = m.group(1);
                    } else {
                        start    = m.start();
                        end      = m.end();
                        original = m.group();
                    }
                    spans.add(new MatchSpan(start, end, original, pt.name()));
                }
            } catch (PatternSyntaxException e) {
                log.error("[Masking] bad regex for " + pt.name() + ": " + e.getMessage());
            }
        }

        if (spans.isEmpty()) {
            return new MaskingResult(text, tokenMap, countByType);
        }

        // 겹치는 범위 제거: 길이 긴 것 우선, 시작 위치 기준 정렬
        Collections.sort(spans, new Comparator<MatchSpan>() {
            public int compare(MatchSpan a, MatchSpan b) {
                if (a.start != b.start) return Integer.compare(a.start, b.start);
                return Integer.compare(b.end - b.start, a.end - a.start); // 길이 긴 것 우선
            }
        });

        List<MatchSpan> nonOverlapping = new ArrayList<MatchSpan>();
        int lastEnd = -1;
        for (MatchSpan span : spans) {
            if (span.start >= lastEnd) {
                nonOverlapping.add(span);
                lastEnd = span.end;
            }
        }

        // 텍스트 재조합
        StringBuilder result = new StringBuilder();
        int cursor = 0;
        for (MatchSpan span : nonOverlapping) {
            result.append(text, cursor, span.start);

            // 토큰 생성
            int cnt = counters.containsKey(span.type) ? counters.get(span.type) : 0;
            cnt++;
            counters.put(span.type, cnt);
            String token = "{{MASK_" + span.type + "_" + cnt + "}}";

            tokenMap.put(token, span.original);
            countByType.put(span.type, cnt);

            result.append(token);
            cursor = span.end;
        }
        result.append(text.substring(cursor));

        return new MaskingResult(result.toString(), tokenMap, countByType);
    }

    // ── 복원 ──────────────────────────────────────────────────────────────────

    /**
     * 마스킹된 텍스트에서 토큰을 원본으로 복원합니다.
     *
     * @param maskedText 토큰이 포함된 텍스트
     * @param tokenMap   {@link MaskingResult#getTokenMap()}에서 얻은 매핑
     * @return 복원된 텍스트
     */
    public String unmask(String maskedText, Map<String, String> tokenMap) {
        if (maskedText == null || tokenMap == null || tokenMap.isEmpty()) {
            return maskedText;
        }
        String result = maskedText;
        for (Map.Entry<String, String> entry : tokenMap.entrySet()) {
            result = result.replace(entry.getKey(), entry.getValue());
        }
        return result;
    }

    // ── 지원 패턴 목록 반환 ───────────────────────────────────────────────────

    public List<Map<String, String>> getSupportedPatterns() {
        List<Map<String, String>> list = new ArrayList<Map<String, String>>();
        for (PatternType pt : PatternType.values()) {
            Map<String, String> m = new LinkedHashMap<String, String>();
            m.put("type",  pt.name());
            m.put("label", pt.label);
            m.put("regex", pt.regex);
            list.add(m);
        }
        return list;
    }

    // ── 내부 유틸 ─────────────────────────────────────────────────────────────

    private boolean isEnabled(String typeName, List<String> enabledTypes) {
        if (enabledTypes == null || enabledTypes.isEmpty()) return true;
        for (String t : enabledTypes) {
            if (typeName.equalsIgnoreCase(t)) return true;
        }
        return false;
    }

    // ── 내부 클래스 ───────────────────────────────────────────────────────────

    private static class MatchSpan {
        final int    start;
        final int    end;
        final String original;
        final String type;

        MatchSpan(int start, int end, String original, String type) {
            this.start    = start;
            this.end      = end;
            this.original = original;
            this.type     = type;
        }
    }

    // ── 결과 클래스 ───────────────────────────────────────────────────────────

    public static class MaskingResult {
        private final String              maskedText;
        private final Map<String, String> tokenMap;    // token → original
        private final Map<String, Integer> countByType; // type  → count

        public MaskingResult(String maskedText,
                             Map<String, String> tokenMap,
                             Map<String, Integer> countByType) {
            this.maskedText   = maskedText;
            this.tokenMap     = tokenMap;
            this.countByType  = countByType;
        }

        public String              getMaskedText()   { return maskedText; }
        public Map<String, String> getTokenMap()     { return tokenMap; }
        public Map<String, Integer> getCountByType() { return countByType; }
        public int getTotalMasked() {
            int total = 0;
            for (int v : countByType.values()) total += v;
            return total;
        }
    }
}
