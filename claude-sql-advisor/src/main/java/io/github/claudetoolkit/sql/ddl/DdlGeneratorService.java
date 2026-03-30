package io.github.claudetoolkit.sql.ddl;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Generates Oracle CREATE TABLE DDL scripts from ERD descriptions.
 *
 * <p>Accepts any of:
 * <ul>
 *   <li>Mermaid {@code erDiagram} text</li>
 *   <li>Free-form table structure description (=== TABLE === ... COL: ...)</li>
 *   <li>Natural language description of tables and relationships</li>
 * </ul>
 *
 * <p>Produces Oracle 11g/12c-compatible DDL with:
 * PRIMARY KEY, FOREIGN KEY, UNIQUE constraints, FK indexes, and COMMENT statements.
 */
public class DdlGeneratorService {

    private static final String SYSTEM_PROMPT =
            "당신은 Oracle DBA 전문가입니다. 제공된 ERD 다이어그램 또는 테이블 구조 설명을 분석하여\n" +
            "Oracle DDL(CREATE TABLE) 스크립트를 생성하세요.\n\n" +
            "출력 규칙:\n" +
            "1. Oracle 11g / 12c 호환 구문만 사용\n" +
            "   - 문자열: VARCHAR2(n) (NVARCHAR2 대신)\n" +
            "   - 숫자: NUMBER(p,s) 또는 NUMBER\n" +
            "   - 날짜: DATE 또는 TIMESTAMP\n" +
            "   - 대용량 텍스트: CLOB\n" +
            "2. 각 테이블에 PRIMARY KEY 제약 조건 포함\n" +
            "3. FOREIGN KEY 관계가 있으면 CONSTRAINT ... FOREIGN KEY ... REFERENCES ... 추가\n" +
            "4. FK 컬럼에는 반드시 CREATE INDEX 추가 (성능)\n" +
            "5. UNIQUE 제약이 필요한 컬럼에는 UNIQUE 추가\n" +
            "6. 모든 컬럼에 COMMENT ON COLUMN 추가 (원본에 한글 설명이 있으면 그대로, 없으면 컬럼명 기반 추측)\n" +
            "7. 스크립트 최상단에 실행 순서 주석 추가 (의존성 순서 반영)\n" +
            "8. 전체 DDL을 하나의 ```sql 코드 블록 안에 작성\n" +
            "9. 각 테이블 앞에 간단한 설명 주석(-- 테이블명: 역할) 추가\n\n" +
            "예시 출력 형식:\n" +
            "```sql\n" +
            "-- ================================================================\n" +
            "-- 실행 순서: T_DEPT → T_EMP\n" +
            "-- ================================================================\n\n" +
            "-- T_DEPT: 부서 정보\n" +
            "CREATE TABLE T_DEPT (\n" +
            "    DEPT_ID   NUMBER       NOT NULL,\n" +
            "    DEPT_NM   VARCHAR2(50) NOT NULL,\n" +
            "    CONSTRAINT PK_DEPT PRIMARY KEY (DEPT_ID)\n" +
            ");\n" +
            "COMMENT ON COLUMN T_DEPT.DEPT_ID IS '부서 ID';\n" +
            "COMMENT ON COLUMN T_DEPT.DEPT_NM IS '부서명';\n" +
            "```";

    private final ClaudeClient claudeClient;

    public DdlGeneratorService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Converts ERD or table structure text to Oracle DDL.
     *
     * @param erdText Mermaid ERD, free-form table description, or natural language
     * @return Oracle CREATE TABLE DDL script in Markdown fenced code block
     */
    public String generateDdl(String erdText) {
        String userMessage = "다음 ERD/테이블 구조를 Oracle DDL로 변환해 주세요:\n\n" + erdText;
        return claudeClient.chat(SYSTEM_PROMPT, userMessage);
    }
}
