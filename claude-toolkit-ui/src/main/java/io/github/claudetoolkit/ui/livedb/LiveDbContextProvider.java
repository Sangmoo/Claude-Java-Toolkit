package io.github.claudetoolkit.ui.livedb;

/**
 * v4.7.x — #G3 Live DB Phase 1: DBMS-별 컨텍스트 수집 인터페이스.
 *
 * <p>구현체는 {@link OracleLiveDbContextProvider} (Phase 1) /
 * PostgresLiveDbContextProvider (Phase 3 / 사용자가 명시 선택 시).
 *
 * <p>주의: 각 구현체는 자신의 {@link ReadOnlyJdbcTemplate} 만 사용. 일반
 * {@code JdbcTemplate} 직접 사용 금지 — 안전 게이트 우회 위험.
 */
public interface LiveDbContextProvider {

    /** "oracle" / "postgres" / "mysql" — DbProfile 의 dbType 과 매칭 */
    String getDbType();

    /**
     * 사용자 SQL 에 대한 라이브 DB 컨텍스트 수집.
     * 정보 일부 수집 실패해도 throw 안 함 — {@link LiveDbContext#getWarnings()} 에 누적.
     *
     * @param userSql  분석할 SQL (read-only 검증은 호출자 책임이 아니라 fetch 내부에서 강제)
     * @param schema   조회할 schema (DBA_TABLES.OWNER) — null 이면 현재 user
     * @param ro       read-only JDBC 게이트
     * @return 수집된 컨텍스트 — 모든 정보 수집 실패해도 빈 객체 반환 (null X)
     */
    LiveDbContext fetch(String userSql, String schema, ReadOnlyJdbcTemplate ro);
}
