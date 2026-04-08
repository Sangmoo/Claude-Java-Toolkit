package io.github.claudetoolkit.ui.translate;

import org.springframework.stereotype.Service;

/**
 * SQL 번역 서비스 — 이종 DB 간 쿼리 변환 시스템 프롬프트를 제공합니다.
 *
 * <p>지원 변환 방향:
 * <ul>
 *   <li>Oracle → PostgreSQL</li>
 *   <li>Oracle → MySQL / MariaDB</li>
 *   <li>MSSQL  → PostgreSQL</li>
 *   <li>MSSQL  → MySQL / MariaDB</li>
 * </ul>
 */
@Service
public class SqlTranslateService {

    /**
     * 소스 DB와 대상 DB에 맞는 번역 시스템 프롬프트를 반환합니다.
     *
     * @param sourceDb "ORACLE" 또는 "MSSQL"
     * @param targetDb "POSTGRESQL", "MYSQL", "MARIADB"
     */
    public String buildSystemPrompt(String sourceDb, String targetDb) {
        StringBuilder sb = new StringBuilder();
        sb.append("당신은 이종 데이터베이스 SQL 마이그레이션 전문가입니다.\n");
        sb.append("입력된 ").append(label(sourceDb)).append(" SQL을 ");
        sb.append(label(targetDb)).append(" 호환 SQL로 정확하게 변환하세요.\n\n");

        // 공통 출력 형식
        sb.append("반드시 아래 3개 섹션 형식으로만 응답하세요:\n\n");
        sb.append("## 1. 변환된 SQL\n");
        sb.append("```sql\n[변환된 SQL 코드]\n```\n\n");
        sb.append("## 2. 주요 변경 사항\n");
        sb.append("[변환 내역을 항목(- )으로 나열. 함수명/구문 변경 등]\n\n");
        sb.append("## 3. 수동 검토 필요 항목 ⚠️\n");
        sb.append("[자동 변환 불가 항목, 없으면 '- 없음' 표기]\n\n");

        // 소스 DB별 변환 규칙
        if ("ORACLE".equalsIgnoreCase(sourceDb)) {
            appendOracleRules(sb, targetDb);
        } else if ("MSSQL".equalsIgnoreCase(sourceDb)) {
            appendMssqlRules(sb, targetDb);
        }

        sb.append("응답은 한국어로 작성하세요.");
        return sb.toString();
    }

    public String buildUserMessage(String sql, String sourceDb, String targetDb) {
        return "다음 " + label(sourceDb) + " SQL을 " + label(targetDb) + "로 변환해주세요.\n\n"
             + "```sql\n" + sql + "\n```";
    }

    // ── 소스 DB 변환 규칙 ──────────────────────────────────────────────────────

    private void appendOracleRules(StringBuilder sb, String targetDb) {
        sb.append("[Oracle → ").append(label(targetDb)).append(" 변환 규칙]\n");

        // 공통 Oracle→표준SQL 규칙
        sb.append("데이터 타입:\n");
        sb.append("  VARCHAR2 → VARCHAR\n");
        sb.append("  NUMBER(p,s) → NUMERIC(p,s) 또는 DECIMAL(p,s)\n");
        sb.append("  NUMBER → NUMERIC\n");
        sb.append("  DATE → TIMESTAMP\n");
        sb.append("  CLOB → TEXT\n");
        sb.append("  BLOB → BYTEA (PostgreSQL) / LONGBLOB (MySQL)\n\n");

        sb.append("함수 변환:\n");
        sb.append("  SYSDATE → NOW() 또는 CURRENT_TIMESTAMP\n");
        sb.append("  NVL(a, b) → COALESCE(a, b)\n");
        sb.append("  NVL2(a, b, c) → CASE WHEN a IS NOT NULL THEN b ELSE c END\n");
        sb.append("  DECODE(col, v1, r1, v2, r2, def) → CASE WHEN ... END\n");
        sb.append("  TO_DATE(str, fmt) → TO_TIMESTAMP(str, fmt) [PG] / STR_TO_DATE(str, fmt) [MySQL]\n");
        sb.append("  TO_CHAR(date, fmt) → TO_CHAR(date, fmt) [PG] / DATE_FORMAT(date, fmt) [MySQL]\n");
        sb.append("  SUBSTR(s, pos, len) → SUBSTRING(s, pos, len)\n");
        sb.append("  INSTR(s, sub) → POSITION(sub IN s) [PG] / LOCATE(sub, s) [MySQL]\n");
        sb.append("  TRUNC(date) → DATE_TRUNC('day', date) [PG] / DATE(date) [MySQL]\n");
        sb.append("  ADD_MONTHS(d, n) → d + INTERVAL 'n months' [PG] / DATE_ADD(d, INTERVAL n MONTH) [MySQL]\n");
        sb.append("  MONTHS_BETWEEN(d1, d2) → EXTRACT(MONTH FROM AGE(d1, d2)) [PG] / TIMESTAMPDIFF(MONTH, d2, d1) [MySQL]\n");
        sb.append("  CONNECT_BY_ISLEAF → 재귀 CTE로 변환 (수동 검토)\n");
        sb.append("  ROWNUM ≤ n → LIMIT n [PG/MySQL]\n");
        sb.append("  ROW_NUMBER() OVER 문법 → 동일 (PG/MySQL 8.0+ 지원)\n");
        sb.append("  CONNECT BY PRIOR → WITH RECURSIVE CTE로 변환\n");
        sb.append("  seq_name.NEXTVAL → NEXTVAL('seq_name') [PG] / AUTO_INCREMENT [MySQL]\n");
        sb.append("  MINUS → EXCEPT [PG] / NOT EXISTS 서브쿼리로 변환 [MySQL]\n");
        sb.append("  (+) 조인 문법 → LEFT JOIN / RIGHT JOIN\n");
        sb.append("  Oracle Hint (/*+ ... */) → 주석 처리\n");
        sb.append("  DBMS_OUTPUT.PUT_LINE → RAISE NOTICE [PG] / SELECT 출력 [MySQL]\n");

        if ("POSTGRESQL".equalsIgnoreCase(targetDb)) {
            sb.append("\nPostgreSQL 특이 사항:\n");
            sb.append("  식별자는 모두 소문자 처리 (대소문자 구분 없이 저장됨)\n");
            sb.append("  시퀀스: CREATE SEQUENCE 또는 SERIAL/BIGSERIAL 타입 사용\n");
            sb.append("  문자열 연결: || 동일 사용 가능\n");
        } else {
            sb.append("\nMySQL/MariaDB 특이 사항:\n");
            sb.append("  AUTO_INCREMENT으로 시퀀스 대체\n");
            sb.append("  백틱(`) 식별자 사용\n");
            sb.append("  MINUS 미지원 → NOT EXISTS 또는 LEFT JOIN ... WHERE IS NULL 패턴 사용\n");
            sb.append("  윈도우 함수: MySQL 8.0+ / MariaDB 10.2+ 에서만 지원\n");
        }
    }

    private void appendMssqlRules(StringBuilder sb, String targetDb) {
        sb.append("[MSSQL → ").append(label(targetDb)).append(" 변환 규칙]\n");

        sb.append("데이터 타입:\n");
        sb.append("  NVARCHAR(n) → VARCHAR(n)\n");
        sb.append("  DATETIME → TIMESTAMP\n");
        sb.append("  DATETIME2 → TIMESTAMP\n");
        sb.append("  BIT → BOOLEAN [PG] / TINYINT(1) [MySQL]\n");
        sb.append("  MONEY → NUMERIC(19,4)\n");
        sb.append("  TEXT/NTEXT → TEXT\n");
        sb.append("  IMAGE → BYTEA [PG] / LONGBLOB [MySQL]\n\n");

        sb.append("함수 변환:\n");
        sb.append("  GETDATE() → NOW()\n");
        sb.append("  ISNULL(a, b) → COALESCE(a, b)\n");
        sb.append("  TOP n → LIMIT n\n");
        sb.append("  DATEADD(unit, n, d) → d + INTERVAL 'n unit' [PG] / DATE_ADD(d, INTERVAL n unit) [MySQL]\n");
        sb.append("  DATEDIFF(unit, d1, d2) → EXTRACT / DATE_PART [PG] / DATEDIFF [MySQL]\n");
        sb.append("  CONVERT(type, val) → CAST(val AS type)\n");
        sb.append("  CHARINDEX(s, str) → POSITION(s IN str) [PG] / LOCATE(s, str) [MySQL]\n");
        sb.append("  LEN(s) → LENGTH(s) 또는 CHAR_LENGTH(s)\n");
        sb.append("  EXCEPT → EXCEPT [PG] / NOT EXISTS [MySQL]\n");
        sb.append("  IDENTITY → SERIAL/BIGSERIAL [PG] / AUTO_INCREMENT [MySQL]\n");
        sb.append("  대괄호 식별자 [col] → 큰따옴표 \"col\" [PG] / 백틱 `col` [MySQL]\n");
    }

    // ── 유틸리티 ──────────────────────────────────────────────────────────────

    private String label(String db) {
        if (db == null) return "";
        switch (db.toUpperCase()) {
            case "ORACLE":     return "Oracle";
            case "MSSQL":      return "SQL Server (MSSQL)";
            case "POSTGRESQL": return "PostgreSQL";
            case "MYSQL":      return "MySQL";
            case "MARIADB":    return "MariaDB";
            default:           return db;
        }
    }
}
