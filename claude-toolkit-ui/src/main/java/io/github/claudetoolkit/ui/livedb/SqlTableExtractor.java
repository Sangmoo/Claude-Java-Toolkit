package io.github.claudetoolkit.ui.livedb;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v4.7.x — #G3 Live DB Phase 1: SELECT 문에서 참조 테이블 이름 추출.
 *
 * <p>{@link OracleLiveDbContextProvider} 가 어떤 테이블의 통계/인덱스를 수집할지
 * 결정하기 위해 사용. 단순 regex 기반 — *완벽하지 않음*. 다음 한계 감수:
 * <ul>
 *   <li>CTE / 서브쿼리 안의 테이블도 *모두* 한 set 으로 추출 (어차피 통계 조회는
 *       super-set 이어도 무해)</li>
 *   <li>derived table / inline view 의 alias 가 다음 FROM 절에서 다시 나타나면
 *       false-positive 가능 — 통계 조회가 빈 결과로 graceful fallback 되므로
 *       기능 영향 없음</li>
 *   <li>schema-qualified 이름 ({@code SCH.T_ORDER}) 은 schema 부분 stripping</li>
 * </ul>
 *
 * <p>이 클래스는 *순수 함수* — 단위 테스트로만 검증.
 */
public final class SqlTableExtractor {

    private SqlTableExtractor() {}

    /**
     * FROM / JOIN 다음에 오는 식별자를 매칭. alias 는 무시 (다음 토큰).
     *
     * <p>패턴 설명:
     * <ul>
     *   <li>{@code (?i)} — case insensitive</li>
     *   <li>{@code \b(?:FROM|JOIN)\s+} — FROM 또는 JOIN 키워드 + 공백</li>
     *   <li>{@code ([A-Za-z_][\w]*(?:\.[A-Za-z_][\w]*)?)} — 식별자 (옵션 schema 포함)</li>
     * </ul>
     */
    private static final Pattern FROM_JOIN_PATTERN = Pattern.compile(
            "(?i)\\b(?:FROM|JOIN)\\s+([A-Za-z_][\\w]*(?:\\.[A-Za-z_][\\w]*)?)");

    /** 주석 제거 패턴 (SqlClassifier 와 동일 정책) */
    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*[\\s\\S]*?\\*/");
    private static final Pattern LINE_COMMENT  = Pattern.compile("--[^\\r\\n]*");

    /**
     * SQL 에서 참조 테이블 이름 추출. 결과는 <b>대문자 정규화</b> + <b>중복 제거</b>
     * (LinkedHashSet 으로 첫 등장 순서 보존).
     *
     * @param sql 분석할 SQL — null/빈 입력은 빈 set 반환
     * @return 테이블 이름 set (대문자, schema 제외, 중복 제거)
     */
    public static Set<String> extract(String sql) {
        Set<String> result = new LinkedHashSet<String>();
        if (sql == null || sql.trim().isEmpty()) return result;

        // 주석 제거 → 매칭 노이즈 줄임
        String cleaned = BLOCK_COMMENT.matcher(sql).replaceAll(" ");
        cleaned = LINE_COMMENT.matcher(cleaned).replaceAll(" ");

        Matcher m = FROM_JOIN_PATTERN.matcher(cleaned);
        while (m.find()) {
            String token = m.group(1);
            // schema.table → table 만 (DBA 통계 조회는 owner 별도 파라미터)
            int dot = token.lastIndexOf('.');
            if (dot >= 0) token = token.substring(dot + 1);
            String upper = token.toUpperCase();
            // SQL 키워드 / alias 류 제외
            if (isSqlKeyword(upper)) continue;
            result.add(upper);
        }
        return result;
    }

    /**
     * FROM/JOIN 다음 토큰이 진짜 테이블이 아닌 SQL 키워드인 경우.
     * 예: {@code SELECT ... FROM (SELECT ...) sub WHERE ...} — FROM 다음 토큰이
     * '(' 가 아닌 식별자라면 정상 테이블.
     *
     * <p>일반적으로 정확한 alias 를 다 잡기는 regex 로 어렵지만, 대문자로 변환된
     * 결과가 SQL 예약어와 일치하면 false-positive 일 가능성이 높음.
     */
    private static boolean isSqlKeyword(String upper) {
        switch (upper) {
            case "SELECT": case "WHERE": case "GROUP": case "ORDER":
            case "HAVING": case "LIMIT": case "OFFSET": case "FETCH":
            case "UNION": case "INTERSECT": case "MINUS": case "EXCEPT":
            case "ON": case "USING": case "AS": case "AND": case "OR":
            case "INNER": case "LEFT": case "RIGHT": case "FULL":
            case "OUTER": case "CROSS": case "LATERAL":
                return true;
            default:
                return false;
        }
    }
}
