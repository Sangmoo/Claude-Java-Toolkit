package io.github.claudetoolkit.ui.livedb;

import java.util.regex.Pattern;

/**
 * v4.7.x — #G3 Live DB Phase 0: SQL 텍스트의 *type* 만 분류.
 *
 * <p>{@link ReadOnlyJdbcTemplate} 의 *유일한* 게이트키퍼. 잘못 분류하면 곧바로
 * 운영 사고로 직결되므로 정책은 다음과 같다:
 * <ul>
 *   <li><b>화이트리스트 only</b>: SELECT / EXPLAIN / DESC / WITH 만 OK 로 판정.
 *       나머지는 명확히 분류해서 거부할 수 있도록 enum 으로 반환.</li>
 *   <li><b>모호하면 UNKNOWN</b>: 빈 문자열, 멀티 statement, 인식 못한 첫 키워드.
 *       safer fail — caller 가 거부.</li>
 *   <li><b>SQL injection 회피 시도 차단</b>: <code>SELECT 1; DELETE FROM T</code>
 *       같이 ; 로 두 statement 가 있으면 하나라도 위험하면 무조건 UNKNOWN.</li>
 *   <li><b>주석 우회 차단</b>: 블록 주석과 라인 주석을 먼저 제거하고 첫 키워드만 본다.
 *       <code>SE&#47;**&#47;LECT</code> 같은 키워드 분할 시도는 주석을 공백으로
 *       치환하면서 첫 단어가 "SE" 가 되어 UNKNOWN 으로 거부됨.</li>
 * </ul>
 *
 * <p>이 클래스는 순수 함수 — 상태 / 외부 의존 0. 단위 테스트로만 검증.
 */
public final class SqlClassifier {

    /** Java 7 호환 — 인스턴스화 차단 */
    private SqlClassifier() {}

    /** Block-comment 제거 패턴 — 단순 lazy 매칭 (nested 비지원). */
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");

    /** Line-comment 제거 패턴 — 줄 끝까지 매칭. */
    private static final Pattern LINE_COMMENT  = Pattern.compile("--[^\\r\\n]*");

    /**
     * 첫 키워드 추출 패턴 — 공백/주석 제거 후 알파벳/언더스코어 만으로 구성된 첫 단어.
     */
    private static final Pattern FIRST_WORD = Pattern.compile("^[A-Za-z_]+");

    /**
     * SQL 분류. 안전한 default 는 {@link SqlOperation#UNKNOWN} (= 거부).
     *
     * @param sql 원본 SQL — null/빈문자열 허용 (UNKNOWN 반환)
     */
    public static SqlOperation classify(String sql) {
        if (sql == null) return SqlOperation.UNKNOWN;
        String stripped = stripCommentsAndNormalize(sql);
        if (stripped.isEmpty()) return SqlOperation.UNKNOWN;

        // 멀티 statement 검사: 주석 제거 후에 ; 가 *마지막* 이외 위치에 있으면 거부.
        // 정상적인 trailing ; 는 허용 (e.g. "SELECT 1;").
        if (containsNonTrailingSemicolon(stripped)) {
            return SqlOperation.UNKNOWN;
        }

        // 첫 키워드 (대문자 정규화)
        java.util.regex.Matcher m = FIRST_WORD.matcher(stripped);
        if (!m.find()) return SqlOperation.UNKNOWN;
        String first = m.group().toUpperCase();

        switch (first) {
            case "SELECT":
                return SqlOperation.SELECT;
            case "WITH":
                return SqlOperation.WITH;       // CTE 포함 SELECT
            case "EXPLAIN":
                return SqlOperation.EXPLAIN;    // EXPLAIN PLAN FOR / EXPLAIN ANALYZE etc.
            case "DESC":
            case "DESCRIBE":
                return SqlOperation.DESC;
            case "INSERT":
            case "UPDATE":
            case "DELETE":
            case "MERGE":
            case "UPSERT":
                return SqlOperation.DML;
            case "CREATE":
            case "DROP":
            case "ALTER":
            case "TRUNCATE":
            case "RENAME":
            case "COMMENT":                     // Oracle COMMENT ON ... 도 DDL 류
                return SqlOperation.DDL;
            case "EXEC":
            case "EXECUTE":
            case "CALL":
            case "BEGIN":                       // PL/SQL 익명 블록
            case "DECLARE":                     // PL/SQL 선언 블록
                return SqlOperation.CALL;
            case "GRANT":
            case "REVOKE":
                return SqlOperation.DCL;
            case "COMMIT":
            case "ROLLBACK":
            case "SAVEPOINT":
            case "SET":                         // SET TRANSACTION 등 — 안전 측 거부
                return SqlOperation.TCL;
            default:
                return SqlOperation.UNKNOWN;
        }
    }

    /**
     * 분류 결과가 read-only 안전한지 — {@link ReadOnlyJdbcTemplate} 의 1차 판정.
     *
     * <p>SELECT / WITH / EXPLAIN / DESC 만 true. 나머지 모두 false (UNKNOWN 포함).
     */
    public static boolean isReadOnly(SqlOperation op) {
        return op == SqlOperation.SELECT
            || op == SqlOperation.WITH
            || op == SqlOperation.EXPLAIN
            || op == SqlOperation.DESC;
    }

    // ── private helpers ───────────────────────────────────────────────────

    /**
     * 주석 제거 + 양 끝 공백 trim. 대소문자는 보존 (FIRST_WORD 가 case-insensitive 처리).
     */
    private static String stripCommentsAndNormalize(String sql) {
        String s = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        s = LINE_COMMENT.matcher(s).replaceAll(" ");
        return s.trim();
    }

    /**
     * 정상 trailing ';' 는 허용. 그 이외 위치 (statement 분할 의도) 는 거부.
     *
     * <p>예:
     * <ul>
     *   <li>{@code SELECT 1}            → false (안전)</li>
     *   <li>{@code SELECT 1;}           → false (trailing 만)</li>
     *   <li>{@code SELECT 1;DELETE T}   → true  (멀티 statement 시도)</li>
     *   <li>{@code SELECT ';' FROM dual} → false (문자열 리터럴 안의 ; 는 무시)</li>
     * </ul>
     */
    private static boolean containsNonTrailingSemicolon(String sql) {
        boolean inSingle = false;
        boolean inDouble = false;
        int len = sql.length();
        for (int i = 0; i < len; i++) {
            char c = sql.charAt(i);
            if (c == '\'' && !inDouble) inSingle = !inSingle;
            else if (c == '"' && !inSingle) inDouble = !inDouble;
            else if (c == ';' && !inSingle && !inDouble) {
                // ; 발견 — 뒤에 의미있는 문자가 더 있는지 확인
                for (int j = i + 1; j < len; j++) {
                    char cj = sql.charAt(j);
                    if (!Character.isWhitespace(cj)) {
                        return true;  // ; 뒤에 다른 statement 존재 — 거부
                    }
                }
                return false;  // 단순 trailing
            }
        }
        return false;
    }
}
