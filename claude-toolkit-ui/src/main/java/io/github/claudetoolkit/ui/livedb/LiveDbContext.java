package io.github.claudetoolkit.ui.livedb;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * v4.7.x — #G3 Live DB Phase 1: 분석에 첨부할 *실시간 DB 컨텍스트* DTO.
 *
 * <p>{@link LiveDbContextProvider} 가 채워서 반환하고,
 * {@link LiveDbContextFormatter} 가 Claude 가 읽을 markdown 으로 변환.
 *
 * <p>모든 필드는 *옵셔널* — 일부 정보 수집 실패해도 가능한 부분만 반환 (graceful
 * degradation). 예: DBA_TABLES 권한이 없어도 EXPLAIN 만 첨부 가능.
 */
public class LiveDbContext {

    /** "Oracle 19c" / "PostgreSQL 14.5" 등 — null 가능 */
    private String dbmsVersion;

    /** SQL 에서 추출된 참조 테이블 (대문자, schema 제외) */
    private List<String> referencedTables = new ArrayList<String>();

    /** 테이블별 통계 — DBA_TABLES + DBA_TAB_STATISTICS 등 */
    private List<TableStats> tables = new ArrayList<TableStats>();

    /** 인덱스 정보 — DBA_INDEXES + DBA_IND_COLUMNS */
    private List<IndexInfo> indexes = new ArrayList<IndexInfo>();

    /** EXPLAIN PLAN 의 사람용 텍스트 출력 (DBMS_XPLAN.DISPLAY 결과 등) — null 가능 */
    private String explainPlanFormatted;

    /** 정보 수집 시점 (Claude prompt 신선도 확인용) */
    private LocalDateTime fetchedAt = LocalDateTime.now();

    /** 정보 수집 중 발생한 오류 메시지 (graceful degradation 디버깅용) */
    private List<String> warnings = new ArrayList<String>();

    public LiveDbContext() {}

    // ── getters / setters ──────────────────────────────────────────────────

    public String getDbmsVersion()                              { return dbmsVersion; }
    public void   setDbmsVersion(String v)                      { this.dbmsVersion = v; }
    public List<String> getReferencedTables()                   { return referencedTables; }
    public void   setReferencedTables(List<String> t)           { this.referencedTables = t != null ? t : new ArrayList<String>(); }
    public List<TableStats> getTables()                         { return tables; }
    public void   setTables(List<TableStats> t)                 { this.tables = t != null ? t : new ArrayList<TableStats>(); }
    public List<IndexInfo> getIndexes()                         { return indexes; }
    public void   setIndexes(List<IndexInfo> i)                 { this.indexes = i != null ? i : new ArrayList<IndexInfo>(); }
    public String getExplainPlanFormatted()                     { return explainPlanFormatted; }
    public void   setExplainPlanFormatted(String s)             { this.explainPlanFormatted = s; }
    public LocalDateTime getFetchedAt()                         { return fetchedAt; }
    public List<String> getWarnings()                           { return Collections.unmodifiableList(warnings); }
    public void   addWarning(String w)                          { if (w != null) this.warnings.add(w); }

    /** 컨텍스트가 *비어있는지* — 모든 정보 수집 실패한 경우 첨부할 가치 없음 */
    public boolean isEmpty() {
        return (dbmsVersion == null || dbmsVersion.isEmpty())
            && tables.isEmpty()
            && indexes.isEmpty()
            && (explainPlanFormatted == null || explainPlanFormatted.isEmpty());
    }

    // ── 내부 record-like 클래스 (Java 8 호환 — final field + ctor) ─────────

    /**
     * 단일 테이블의 통계 메타.
     */
    public static final class TableStats {
        public final String name;             // T_ORDER 등
        public final Long   numRows;          // null 가능 (통계 미수집)
        public final Long   numBlocks;
        public final LocalDateTime lastAnalyzed;
        public final String comment;          // DBA_TAB_COMMENTS

        public TableStats(String name, Long numRows, Long numBlocks,
                          LocalDateTime lastAnalyzed, String comment) {
            this.name         = name;
            this.numRows      = numRows;
            this.numBlocks    = numBlocks;
            this.lastAnalyzed = lastAnalyzed;
            this.comment      = comment;
        }
    }

    /**
     * 인덱스 정보 — 컬럼 별 position 별로 한 row.
     * 같은 인덱스의 여러 컬럼은 indexName 으로 그룹핑됨.
     */
    public static final class IndexInfo {
        public final String tableName;
        public final String indexName;
        public final String columnName;
        public final int    columnPosition;     // 1-based
        public final boolean unique;
        public final Long   distinctKeys;       // cardinality

        public IndexInfo(String tableName, String indexName, String columnName,
                         int columnPosition, boolean unique, Long distinctKeys) {
            this.tableName      = tableName;
            this.indexName      = indexName;
            this.columnName     = columnName;
            this.columnPosition = columnPosition;
            this.unique         = unique;
            this.distinctKeys   = distinctKeys;
        }
    }
}
