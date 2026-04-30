package io.github.claudetoolkit.ui.livedb;

/**
 * v4.7.x — #G3 Live DB Phase 0: SQL 의 종류 분류.
 *
 * <p>{@link SqlClassifier} 가 SQL 텍스트를 받아 이 enum 으로 반환한다.
 * {@link ReadOnlyJdbcTemplate} 는 {@link #SELECT}, {@link #EXPLAIN},
 * {@link #DESC} 만 통과시키고 나머지는 {@link SecurityException} 을 던진다.
 *
 * <p><b>UNKNOWN</b> 의 의미: 분류 실패 (빈 문자열 / 멀티 statement / 인식 못함).
 * 안전 측에서 거부 — "확실히 안전하다고 판정된 것만 통과" 정책.
 */
public enum SqlOperation {
    /** 단일 SELECT 쿼리 — 읽기 전용. 통과 허용. */
    SELECT,
    /** EXPLAIN PLAN FOR / EXPLAIN — 실행계획 조회. 통과 허용. */
    EXPLAIN,
    /** DESC / DESCRIBE — 테이블 구조 조회. 통과 허용. */
    DESC,
    /** WITH ... SELECT — CTE 가 포함된 SELECT. 통과 허용. */
    WITH,
    /** INSERT/UPDATE/DELETE/MERGE — 데이터 변경. 거부. */
    DML,
    /** CREATE/DROP/ALTER/TRUNCATE/RENAME — 스키마 변경. 거부. */
    DDL,
    /** EXEC / CALL / BEGIN PL/SQL — 저장 프로시저 실행. 거부 (부수효과 가능). */
    CALL,
    /** GRANT / REVOKE — 권한 변경. 거부. */
    DCL,
    /** COMMIT / ROLLBACK / SAVEPOINT — 트랜잭션 제어. 거부 (분석 채널에 불필요). */
    TCL,
    /**
     * 분류 실패 — 빈 문자열, 다중 statement (`;` 분할), 인식 못한 키워드 등.
     * 안전 측 default — 거부.
     */
    UNKNOWN
}
