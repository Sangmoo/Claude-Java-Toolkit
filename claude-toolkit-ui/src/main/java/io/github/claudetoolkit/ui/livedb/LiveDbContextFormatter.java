package io.github.claudetoolkit.ui.livedb;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.7.x — #G3 Live DB Phase 1: {@link LiveDbContext} 를 Claude prompt 용 markdown 으로 변환.
 *
 * <p>출력은 다음 섹션으로 구성 — 각 섹션은 데이터가 있을 때만 포함:
 * <ol>
 *   <li>DBMS 헤더 (버전 + 수집 시각)</li>
 *   <li>테이블 통계 표 (NUM_ROWS / BLOCKS / LAST_ANALYZED)</li>
 *   <li>인덱스 — 테이블별 그룹핑된 list</li>
 *   <li>EXPLAIN PLAN — 코드 블록 안에 그대로</li>
 *   <li>경고 (정보 수집 실패) — 디버깅 컨텍스트</li>
 * </ol>
 *
 * <p>전체 길이 4000 chars 상한 — 토큰 폭발 방지. 초과 시 EXPLAIN 우선 truncate
 * (가장 길고 그래도 의미 보존되는 부분).
 */
public final class LiveDbContextFormatter {

    private static final int MAX_TOTAL_LENGTH = 4000;
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private LiveDbContextFormatter() {}

    /**
     * 컨텍스트를 markdown 으로 변환. {@link LiveDbContext#isEmpty()} 인 경우 빈 문자열.
     */
    public static String format(LiveDbContext ctx) {
        if (ctx == null || ctx.isEmpty()) return "";

        StringBuilder sb = new StringBuilder();

        // 1. 헤더
        sb.append("**[실시간 DB 메타]**\n");
        if (ctx.getDbmsVersion() != null && !ctx.getDbmsVersion().isEmpty()) {
            sb.append("- DBMS: ").append(ctx.getDbmsVersion()).append("\n");
        }
        if (ctx.getFetchedAt() != null) {
            sb.append("- 수집 시각: ").append(ctx.getFetchedAt().format(TS)).append("\n");
        }
        sb.append('\n');

        // 2. 테이블 통계
        if (!ctx.getTables().isEmpty()) {
            sb.append("**테이블 통계**\n\n");
            sb.append("| 테이블 | NUM_ROWS | BLOCKS | LAST_ANALYZED | 코멘트 |\n");
            sb.append("|--------|----------|--------|---------------|--------|\n");
            for (LiveDbContext.TableStats t : ctx.getTables()) {
                sb.append("| ").append(t.name)
                  .append(" | ").append(formatNumber(t.numRows))
                  .append(" | ").append(formatNumber(t.numBlocks))
                  .append(" | ").append(t.lastAnalyzed != null ? t.lastAnalyzed.format(TS) : "-")
                  .append(" | ").append(t.comment != null ? truncate(t.comment, 40) : "")
                  .append(" |\n");
            }
            sb.append('\n');
        }

        // 3. 인덱스 — 테이블별 그룹핑
        if (!ctx.getIndexes().isEmpty()) {
            sb.append("**인덱스**\n\n");
            // {tableName -> {indexName -> ordered columns}}
            Map<String, Map<String, List<LiveDbContext.IndexInfo>>> grouped =
                    new LinkedHashMap<String, Map<String, List<LiveDbContext.IndexInfo>>>();
            for (LiveDbContext.IndexInfo idx : ctx.getIndexes()) {
                Map<String, List<LiveDbContext.IndexInfo>> byIndex =
                        grouped.computeIfAbsent(idx.tableName,
                                k -> new LinkedHashMap<String, List<LiveDbContext.IndexInfo>>());
                byIndex.computeIfAbsent(idx.indexName,
                        k -> new ArrayList<LiveDbContext.IndexInfo>()).add(idx);
            }
            for (Map.Entry<String, Map<String, List<LiveDbContext.IndexInfo>>> tableEntry : grouped.entrySet()) {
                sb.append("- *").append(tableEntry.getKey()).append("*\n");
                for (Map.Entry<String, List<LiveDbContext.IndexInfo>> idxEntry : tableEntry.getValue().entrySet()) {
                    List<LiveDbContext.IndexInfo> cols = idxEntry.getValue();
                    StringBuilder colsSb = new StringBuilder();
                    boolean unique = false;
                    Long distinctKeys = null;
                    for (int i = 0; i < cols.size(); i++) {
                        if (i > 0) colsSb.append(", ");
                        colsSb.append(cols.get(i).columnName);
                        if (cols.get(i).unique) unique = true;
                        if (cols.get(i).distinctKeys != null) distinctKeys = cols.get(i).distinctKeys;
                    }
                    sb.append("  - `").append(idxEntry.getKey()).append("` (").append(colsSb).append(")");
                    if (unique) sb.append(" — UNIQUE");
                    if (distinctKeys != null) sb.append(" — distinct_keys=").append(distinctKeys);
                    sb.append('\n');
                }
            }
            sb.append('\n');
        }

        // 4. EXPLAIN PLAN — 코드 블록 안에. 가장 길어질 수 있는 섹션 — 추후 truncate 대상.
        String explain = ctx.getExplainPlanFormatted();
        if (explain != null && !explain.isEmpty()) {
            sb.append("**EXPLAIN PLAN**\n\n```\n");
            sb.append(explain);
            if (!explain.endsWith("\n")) sb.append('\n');
            sb.append("```\n\n");
        }

        // 5. 경고 (graceful fallback 디버깅)
        if (!ctx.getWarnings().isEmpty()) {
            sb.append("> ⚠️ 일부 정보 수집 실패:\n");
            for (String w : ctx.getWarnings()) {
                sb.append("> - ").append(w).append('\n');
            }
        }

        return enforceMaxLength(sb.toString());
    }

    /**
     * 4000 chars 초과 시 EXPLAIN PLAN 부분을 우선 truncate. EXPLAIN 이 없으면
     * 마지막 섹션부터 잘라냄.
     */
    private static String enforceMaxLength(String s) {
        if (s.length() <= MAX_TOTAL_LENGTH) return s;
        // EXPLAIN 코드블록의 시작/끝 위치 찾기
        int explainStart = s.indexOf("**EXPLAIN PLAN**");
        if (explainStart > 0) {
            int beforeLen = explainStart;
            int budget    = MAX_TOTAL_LENGTH - beforeLen - 100;  // truncation note 자리 100자 확보
            if (budget > 200) {
                String before = s.substring(0, explainStart);
                String afterCodeBlock = s.substring(explainStart);
                String truncated = afterCodeBlock.length() > budget
                        ? afterCodeBlock.substring(0, budget) + "\n... (이하 EXPLAIN PLAN 길이 초과로 생략)\n```\n"
                        : afterCodeBlock;
                return before + truncated;
            }
        }
        // EXPLAIN 이 없는 경우에는 끝에서 자름
        return s.substring(0, MAX_TOTAL_LENGTH - 50) + "\n... (이하 길이 초과로 생략)";
    }

    private static String formatNumber(Long n) {
        if (n == null) return "-";
        return String.format("%,d", n);
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }
}
