package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — #G3 Live DB Phase 1: LiveDbContextFormatter 테스트.
 *
 * <p>출력 형식 + 4000 chars 상한 + graceful empty 처리 검증.
 */
class LiveDbContextFormatterTest {

    @Test
    @DisplayName("빈 컨텍스트 — 빈 문자열")
    void emptyContext_emptyString() {
        LiveDbContext ctx = new LiveDbContext();
        assertEquals("", LiveDbContextFormatter.format(ctx));
        assertEquals("", LiveDbContextFormatter.format(null));
    }

    @Test
    @DisplayName("DBMS 버전만 있어도 정상 출력")
    void versionOnly() {
        LiveDbContext ctx = new LiveDbContext();
        ctx.setDbmsVersion("Oracle 19c Enterprise");
        String md = LiveDbContextFormatter.format(ctx);
        assertTrue(md.contains("Oracle 19c"));
        assertTrue(md.contains("[실시간 DB 메타]"));
    }

    @Test
    @DisplayName("테이블 통계 — 표 형식 + 천단위 콤마")
    void tableStatsFormatted() {
        LiveDbContext ctx = new LiveDbContext();
        ctx.setDbmsVersion("Oracle 19c");
        ctx.setTables(Collections.singletonList(
                new LiveDbContext.TableStats(
                        "T_ORDER", 12450000L, 142000L,
                        LocalDateTime.of(2026, 4, 25, 3, 0),
                        "주문 마스터")));
        String md = LiveDbContextFormatter.format(ctx);
        assertTrue(md.contains("**테이블 통계**"));
        assertTrue(md.contains("T_ORDER"));
        assertTrue(md.contains("12,450,000"), "천단위 콤마: " + md);
        assertTrue(md.contains("주문 마스터"));
        assertTrue(md.contains("2026-04-25"));
    }

    @Test
    @DisplayName("인덱스 — 같은 인덱스의 여러 컬럼은 한 줄에 그룹핑")
    void indexesGrouped() {
        LiveDbContext ctx = new LiveDbContext();
        ctx.setIndexes(Arrays.asList(
                new LiveDbContext.IndexInfo("T_ORDER", "IDX_DATE_STATUS", "ORDER_DATE", 1, false, 5000L),
                new LiveDbContext.IndexInfo("T_ORDER", "IDX_DATE_STATUS", "STATUS",     2, false, 5000L),
                new LiveDbContext.IndexInfo("T_ORDER", "PK_T_ORDER",      "ORDER_ID",   1, true,  null)));
        String md = LiveDbContextFormatter.format(ctx);
        assertTrue(md.contains("**인덱스**"));
        assertTrue(md.contains("`IDX_DATE_STATUS` (ORDER_DATE, STATUS)"),
                   "복합 인덱스 그룹핑: " + md);
        assertTrue(md.contains("`PK_T_ORDER` (ORDER_ID) — UNIQUE"),
                   "UNIQUE 표시: " + md);
        assertTrue(md.contains("distinct_keys=5000"));
    }

    @Test
    @DisplayName("EXPLAIN PLAN — 코드 블록 안에")
    void explainInsideCodeBlock() {
        LiveDbContext ctx = new LiveDbContext();
        ctx.setExplainPlanFormatted("Plan hash value: 12345\n" +
                "| Id | Operation        | Name |\n" +
                "|  0 | SELECT STATEMENT |      |");
        String md = LiveDbContextFormatter.format(ctx);
        assertTrue(md.contains("**EXPLAIN PLAN**"));
        assertTrue(md.contains("```"), "코드 블록 시작");
        assertTrue(md.contains("Plan hash value"));
    }

    @Test
    @DisplayName("Warnings — 인용문 형식")
    void warnings() {
        LiveDbContext ctx = new LiveDbContext();
        ctx.setDbmsVersion("Oracle");
        ctx.addWarning("DBA_TABLES 권한 없음");
        ctx.addWarning("EXPLAIN PLAN 실패");
        String md = LiveDbContextFormatter.format(ctx);
        assertTrue(md.contains("⚠️"));
        assertTrue(md.contains("DBA_TABLES 권한 없음"));
        assertTrue(md.contains("EXPLAIN PLAN 실패"));
    }

    @Test
    @DisplayName("4000 chars 상한 — 큰 EXPLAIN 은 truncate")
    void maxLengthEnforced() {
        LiveDbContext ctx = new LiveDbContext();
        ctx.setDbmsVersion("Oracle 19c");
        // 5000자짜리 fake EXPLAIN
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 200; i++) {
            big.append("| ").append(i)
               .append(" | TABLE ACCESS FULL | T_VERY_LONG_NAME_").append(i).append(" |\n");
        }
        ctx.setExplainPlanFormatted(big.toString());
        String md = LiveDbContextFormatter.format(ctx);
        assertTrue(md.length() <= 4000, "상한 4000: " + md.length());
        // truncation note 포함 (혹은 정상 종료)
        assertTrue(md.contains("EXPLAIN PLAN") || md.contains("길이 초과"));
    }

    @Test
    @DisplayName("null TableStats 필드 — '-' 표시 + NPE 없음")
    void nullFieldsRenderedAsDash() {
        LiveDbContext ctx = new LiveDbContext();
        ctx.setTables(Collections.singletonList(
                new LiveDbContext.TableStats("T_NEW", null, null, null, null)));
        String md = LiveDbContextFormatter.format(ctx);
        assertTrue(md.contains("T_NEW"));
        // NUM_ROWS 가 null 이면 "-" 로
        assertTrue(md.contains("- |"), "null 필드는 '-' 로 표시: " + md);
    }
}
