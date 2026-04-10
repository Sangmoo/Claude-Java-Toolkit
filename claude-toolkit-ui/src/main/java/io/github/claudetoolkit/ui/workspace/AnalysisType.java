package io.github.claudetoolkit.ui.workspace;

import java.util.Arrays;
import java.util.List;

/**
 * 워크스페이스에서 지원하는 분석 유형.
 * <p>각 유형은 표시명, 지원 언어, 설명을 갖습니다.
 */
public enum AnalysisType {

    CODE_REVIEW(
        "코드 리뷰",
        Arrays.asList("java", "kotlin", "python", "javascript", "typescript"),
        "Java/Spring 코드 품질, 설계 패턴, 버그 위험 분석"
    ),
    SECURITY_AUDIT(
        "보안 감사",
        Arrays.asList("java", "kotlin", "python", "javascript", "typescript"),
        "OWASP Top 10 기준 보안 취약점 탐지"
    ),
    TEST_GENERATION(
        "테스트 코드 생성",
        Arrays.asList("java", "kotlin", "python", "javascript", "typescript"),
        "JUnit 5 / pytest / Jest 테스트 코드 자동 생성"
    ),
    JAVADOC(
        "Javadoc 생성",
        Arrays.asList("java", "kotlin", "python", "javascript", "typescript"),
        "소스 코드에 언어별 문서 주석 자동 추가"
    ),
    REFACTOR(
        "리팩터링 제안",
        Arrays.asList("java", "kotlin", "python", "javascript", "typescript"),
        "코드 구조 개선, 패턴 적용, 가독성 향상 제안"
    ),
    SQL_REVIEW(
        "SQL 리뷰",
        Arrays.asList("sql"),
        "Oracle SQL 성능 문제, 안티패턴, 인덱스 제안"
    ),
    SQL_SECURITY(
        "SQL 보안 감사",
        Arrays.asList("sql"),
        "SQL Injection, 권한 노출, 민감 데이터 접근 취약점 탐지"
    ),
    SQL_TRANSLATE(
        "SQL DB 번역",
        Arrays.asList("sql"),
        "이종 DB 간 SQL 문법 변환 (Oracle↔MySQL↔PostgreSQL↔MSSQL)"
    ),
    HARNESS(
        "코드 리뷰 하네스",
        Arrays.asList("java", "sql", "kotlin"),
        "Analyst→Builder→Reviewer→Verifier 4단계 AI 파이프라인"
    ),
    AI_CHAT(
        "AI 채팅",
        Arrays.asList("java", "sql", "kotlin", "python", "javascript", "typescript"),
        "대화형 AI 질의응답 (코드 분석, SQL 최적화, 아키텍처 상담)"
    );

    /** 화면 표시명 */
    public final String displayName;
    /** 지원 언어 목록 */
    public final List<String> supportedLanguages;
    /** 기능 설명 */
    public final String description;

    AnalysisType(String displayName, List<String> supportedLanguages, String description) {
        this.displayName        = displayName;
        this.supportedLanguages = supportedLanguages;
        this.description        = description;
    }

    /**
     * 지정한 언어를 지원하는지 확인합니다.
     *
     * @param language 언어 식별자 (예: "java", "sql")
     * @return 지원 여부
     */
    public boolean supports(String language) {
        if (language == null) return false;
        for (String lang : supportedLanguages) {
            if (lang.equalsIgnoreCase(language)) return true;
        }
        return false;
    }
}
