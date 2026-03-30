package io.github.claudetoolkit.docgen.converter;

import io.github.claudetoolkit.starter.client.ClaudeClient;

/**
 * Converts Oracle PL/SQL code to Java/Spring + MyBatis using Claude API.
 */
public class CodeConverterService {

    private static final String SYSTEM_IBATIS_TO_MYBATIS =
        "당신은 iBatis(SqlMap) → MyBatis 마이그레이션 전문가입니다.\n" +
        "iBatis XML 매퍼를 MyBatis 3.x 형식으로 변환하세요.\n" +
        "변환 규칙:\n" +
        "1. <sqlMap> 루트 태그 → <mapper namespace=\"...\" > (파일명 기반으로 namespace 추정)\n" +
        "2. <select/insert/update/delete id=...> 동일하게 유지, parameterClass/resultClass → parameterType/resultType\n" +
        "3. <typeAlias> 제거 후 FQCN 또는 간단한 타입명 사용\n" +
        "4. #value# → #{value}, $value$ → ${value}\n" +
        "5. <dynamic prepend=...> → <where>, <if>, <choose> 등 OGNL 기반 동적 SQL\n" +
        "6. <isNotNull>, <isNotEmpty> → <if test=\"field != null and field != ''\">  \n" +
        "7. <iterate> → <foreach>\n" +
        "8. resultMap이 있으면 MyBatis resultMap 형식으로 변환\n" +
        "변환된 전체 XML 파일을 반환하세요. 변경 사항 요약도 주석으로 파일 상단에 포함하세요.";

    private static final String SYSTEM_SP_TO_JAVA =
            "당신은 Oracle PL/SQL과 Java/Spring 개발 전문가입니다.\n" +
            "Oracle Stored Procedure 또는 PL/SQL 코드를 Java/Spring + MyBatis 코드로 변환합니다.\n\n" +
            "변환 규칙:\n" +
            "1. Oracle 커서(CURSOR, REF CURSOR) → Java List<VO> 또는 List<Map<String,Object>>로 변환\n" +
            "2. Oracle 예외처리(EXCEPTION WHEN) → Java try-catch + Custom Exception으로 변환\n" +
            "3. IN/OUT 파라미터는 DTO/VO 클래스로 정의\n" +
            "4. SELECT는 MyBatis XML 매퍼 + Java Mapper 인터페이스로 변환\n" +
            "5. DML(INSERT/UPDATE/DELETE)은 MyBatis XML 매퍼로 변환\n" +
            "6. Spring @Service, @Repository 어노테이션 사용\n" +
            "7. 트랜잭션은 @Transactional 처리\n" +
            "8. 한국어 주석 유지\n\n" +
            "출력 형식: 각 파일(VO.java, XxxMapper.java, XxxMapper.xml, XxxService.java, XxxServiceImpl.java)을\n" +
            "--- 파일명 --- 구분선으로 명확히 분리하여 출력하세요.";

    private static final String SYSTEM_JAVA_TO_SP =
            "당신은 Java/Spring과 Oracle PL/SQL 전문가입니다.\n" +
            "Java Service/Repository 코드와 MyBatis XML 매퍼를 Oracle Stored Procedure로 변환합니다.\n\n" +
            "변환 규칙:\n" +
            "1. Spring @Service 메서드 → Oracle PROCEDURE 또는 FUNCTION으로 변환\n" +
            "2. Java List<VO>/List<Map> 반환 → OUT SYS_REFCURSOR 파라미터로 변환\n" +
            "3. Java try-catch/Custom Exception → EXCEPTION WHEN OTHERS 처리로 변환\n" +
            "4. DTO/VO 파라미터 → IN/OUT 파라미터로 변환\n" +
            "5. MyBatis XML <select>/<insert>/<update>/<delete> → 해당 DML/SELECT 포함\n" +
            "6. @Transactional → BEGIN...COMMIT/ROLLBACK 트랜잭션 블록으로 처리\n" +
            "7. 한국어 주석 유지\n" +
            "8. CREATE OR REPLACE PROCEDURE/FUNCTION 형식으로 출력\n\n" +
            "출력 형식: 각 프로시저/패키지를 --- 프로시저명 --- 구분선으로 명확히 분리하여 출력하세요.\n" +
            "필요 시 PACKAGE SPEC + BODY 형태로 묶어주세요.";

    private static final String SYSTEM_SQL_TO_MYBATIS =
            "당신은 MyBatis/iBatis XML 매퍼 전문가입니다.\n" +
            "Oracle SQL 쿼리를 MyBatis 3 XML 매퍼 형식으로 변환합니다.\n\n" +
            "변환 규칙:\n" +
            "1. SELECT → <select> 태그, resultMap 또는 resultType 정의\n" +
            "2. INSERT → <insert> 태그, parameterType 정의\n" +
            "3. UPDATE → <update> 태그, <set>/<if> 태그 활용\n" +
            "4. DELETE → <delete> 태그\n" +
            "5. 동적 쿼리 조건은 <if>, <choose>, <where>, <foreach> 태그 활용\n" +
            "6. namespace는 패키지명.MapperInterface 형식 (예: com.example.mapper.OrderMapper)\n" +
            "7. resultMap은 컬럼명과 Java 필드명 매핑 명시\n\n" +
            "반드시 완전한 XML DOCTYPE 포함 MyBatis XML 형식으로 출력하세요.";

    private final ClaudeClient claudeClient;

    public CodeConverterService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    /**
     * Convert Oracle Stored Procedure to Java/Spring + MyBatis.
     */
    public String convertSpToJava(String oracleCode) {
        String userMessage = "다음 Oracle Stored Procedure를 Java/Spring + MyBatis로 변환해주세요:\n\n```sql\n"
                + oracleCode + "\n```";
        return claudeClient.chat(SYSTEM_SP_TO_JAVA, userMessage);
    }

    /**
     * Convert Oracle SQL to MyBatis XML mapper.
     */
    public String convertSqlToMyBatis(String sqlCode) {
        String userMessage = "다음 Oracle SQL을 MyBatis XML 매퍼로 변환해주세요:\n\n```sql\n"
                + sqlCode + "\n```";
        return claudeClient.chat(SYSTEM_SQL_TO_MYBATIS, userMessage);
    }

    /**
     * Convert Java/Spring + MyBatis code to Oracle Stored Procedure.
     */
    public String convertJavaToSp(String javaCode) {
        String userMessage = "다음 Java/Spring + MyBatis 코드를 Oracle Stored Procedure로 변환해주세요:\n\n```java\n"
                + javaCode + "\n```";
        return claudeClient.chat(SYSTEM_JAVA_TO_SP, userMessage);
    }

    /**
     * Convert based on target type.
     *
     * @param sourceCode  source code to convert
     * @param targetType  "mybatis" for Oracle SQL→MyBatis XML,
     *                    "java"    for Oracle SP→Java/Spring+MyBatis,
     *                    "java_to_sp" for Java/Spring+MyBatis→Oracle SP
     */
    public String convert(String sourceCode, String targetType) {
        if ("mybatis".equalsIgnoreCase(targetType)) {
            return convertSqlToMyBatis(sourceCode);
        } else if ("java_to_sp".equalsIgnoreCase(targetType)) {
            return convertJavaToSp(sourceCode);
        } else if ("ibatis".equalsIgnoreCase(targetType)) {
            return convertIBatisToMyBatis(sourceCode);
        }
        return convertSpToJava(sourceCode);
    }

    public String convertIBatisToMyBatis(String ibatisXml) {
        return claudeClient.chat(SYSTEM_IBATIS_TO_MYBATIS, ibatisXml);
    }
}
