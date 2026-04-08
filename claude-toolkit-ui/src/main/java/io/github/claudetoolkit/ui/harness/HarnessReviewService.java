package io.github.claudetoolkit.ui.harness;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * Harness pipeline service: Analyst → Builder → Reviewer
 *
 * <p>각 단계를 <strong>독립된 3번의 API 호출</strong>로 분리합니다.
 * <ul>
 *   <li>1단계 Analyst  — 문제점 분석 목록 (max 2048 토큰)</li>
 *   <li>2단계 Builder  — 완성된 개선 코드 (max 8192 토큰)</li>
 *   <li>3단계 Reviewer — 변경 내역·기대 효과·최종 판정 (max 3072 토큰)</li>
 * </ul>
 *
 * <p>단일 호출로 전체 5개 섹션을 한번에 요청하던 구조에서는 대형 SP/클래스를
 * 분석할 때 max_tokens 한도 내에 응답이 끝나지 않아 변경 내역·검토 결과가
 * 잘리는 문제가 있었습니다. 분리 호출 방식은 각 단계가 집중된 작업만 수행하므로
 * 토큰 한도를 초과하지 않습니다.
 *
 * <p>Builder 단계는 {@link io.github.claudetoolkit.starter.client.ClaudeClient#chatWithContinuation}
 * / {@code chatStreamWithContinuation}을 사용하므로, 8192 토큰을 초과하는 대형 SP도
 * 이어쓰기(continuation)로 완전하게 출력됩니다 (최대 3회 추가 호출 = 약 32,768 토큰).
 *
 * <p>4단계 Verifier는 개선된 코드가 실제로 사용 가능한지를 정적 분석 관점에서 검증합니다:
 * Java — 컴파일 가능성·Spring/JPA 호환성·위험 변경 감지,
 * SQL  — SQL 문법 오류·Oracle 의존성 깨짐·위험 변경 감지(DROP/TRUNCATE/DELETE without WHERE).
 */
@Service
public class HarnessReviewService {

    /** Analyst: 분석 결과는 항목 목록이므로 짧습니다. */
    private static final int TOKENS_ANALYST        = 2048;
    /** Builder: 전체 개선 코드를 출력하므로 가장 큰 예산이 필요합니다. */
    private static final int TOKENS_BUILDER        = 8192;
    /** Reviewer: 변경 내역·기대 효과·품질 점수·최종 판정 4개 섹션. */
    private static final int TOKENS_REVIEWER       = 4096;
    /** Verifier: 컴파일·문법·위험·의존성 4개 섹션 + 검증 판정. */
    private static final int TOKENS_VERIFIER       = 2048;
    /**
     * Builder 단계 이어쓰기 최대 횟수.
     * 1회 추가 = 최대 16,384 토큰, 2회 = 24,576 토큰, 3회 = 32,768 토큰 출력 가능.
     * 대부분의 대형 SP/패키지는 3회 이내에 완결됩니다.
     */
    private static final int BUILDER_CONTINUATIONS = 3;

    private final ClaudeClient    claudeClient;
    private final ToolkitSettings settings;

    public HarnessReviewService(ClaudeClient claudeClient, ToolkitSettings settings) {
        this.claudeClient = claudeClient;
        this.settings     = settings;
    }

    // ── 파이프라인: 비스트리밍 ─────────────────────────────────────────────────

    /**
     * 3단계 파이프라인을 순차 실행하고, UI가 기대하는 ## 섹션 형식으로 조립한 결과를 반환합니다.
     */
    public String analyze(String code, String language) {
        return analyze(code, language, "");
    }

    public String analyze(String code, String language, String templateHint) {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String codeBlock = isSql ? "sql" : "java";
        String memo      = settings.getProjectContext();

        // 1단계: Analyst — 문제점 분석
        String analysis = claudeClient.chat(
                applyTemplateHint(withMemo(buildAnalystSystem(language), memo), templateHint),
                buildAnalystUser(code, language),
                TOKENS_ANALYST);

        // 2단계: Builder — 개선 코드 작성 (이어쓰기로 대형 SP도 완전 출력 보장)
        String rawImproved = claudeClient.chatWithContinuation(
                withMemo(buildBuilderSystem(language), memo),
                buildBuilderUser(code, analysis.trim(), language),
                TOKENS_BUILDER, BUILDER_CONTINUATIONS);
        String improved = stripCodeFences(rawImproved.trim(), language);

        // 3단계: Reviewer — 변경 내역·기대 효과·최종 판정
        String reviewSections = claudeClient.chat(
                withMemo(buildReviewerSystem(language), memo),
                buildReviewerUser(code, improved, analysis.trim(), language),
                TOKENS_REVIEWER);

        // 4단계: Verifier — 컴파일·문법·위험·의존성 검증
        String verifyResult = claudeClient.chat(
                withMemo(buildVerifierSystem(language), memo),
                buildVerifierUser(code, improved, analysis.trim(), language),
                TOKENS_VERIFIER);

        // UI extractSection() 이 기대하는 ## 섹션 형식으로 조립
        return "## 📋 분석 요약\n" + analysis.trim()
             + "\n\n## 🔧 개선된 코드\n```" + codeBlock + "\n" + improved + "\n```"
             + "\n\n" + reviewSections.trim()
             + "\n\n" + verifyResult.trim();
    }

    // ── 파이프라인: SSE 스트리밍 ──────────────────────────────────────────────

    /**
     * 3단계를 순차 스트리밍합니다. 각 단계의 헤더를 직접 emit한 뒤 해당 단계의
     * Claude 응답을 실시간으로 흘려보냅니다.
     *
     * @param code     분석할 소스 코드
     * @param language "java" 또는 "sql"
     * @param onChunk  텍스트 청크를 수신할 콜백
     */
    public void analyzeStream(String code, String language,
                              Consumer<String> onChunk) throws IOException {
        analyzeStream(code, language, "", onChunk);
    }

    public void analyzeStream(String code, String language, String templateHint,
                              Consumer<String> onChunk) throws IOException {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String codeBlock = isSql ? "sql" : "java";
        String memo      = settings.getProjectContext();

        // ── 1단계: Analyst ────────────────────────────────────────────────────
        onChunk.accept("## 📋 분석 요약\n");
        final StringBuilder analysisBuf = new StringBuilder();
        claudeClient.chatStream(
                applyTemplateHint(withMemo(buildAnalystSystem(language), memo), templateHint),
                buildAnalystUser(code, language),
                TOKENS_ANALYST,
                new Consumer<String>() {
                    public void accept(String chunk) {
                        analysisBuf.append(chunk);
                        onChunk.accept(chunk);
                    }
                });
        String analysis = analysisBuf.toString().trim();

        // ── 2단계: Builder — 이어쓰기로 대형 SP도 완전 출력 보장 ──────────────
        onChunk.accept("\n\n## 🔧 개선된 코드\n```" + codeBlock + "\n");
        final StringBuilder improvedBuf = new StringBuilder();
        claudeClient.chatStreamWithContinuation(
                withMemo(buildBuilderSystem(language), memo),
                buildBuilderUser(code, analysis, language),
                TOKENS_BUILDER, BUILDER_CONTINUATIONS,
                new Consumer<String>() {
                    public void accept(String chunk) {
                        improvedBuf.append(chunk);
                        onChunk.accept(chunk);
                    }
                });
        String improved = stripCodeFences(improvedBuf.toString().trim(), language);
        onChunk.accept("\n```\n");

        // ── 3단계: Reviewer ───────────────────────────────────────────────────
        onChunk.accept("\n");
        claudeClient.chatStream(
                withMemo(buildReviewerSystem(language), memo),
                buildReviewerUser(code, improved, analysis, language),
                TOKENS_REVIEWER,
                onChunk);

        // ── 4단계: Verifier ───────────────────────────────────────────────────
        onChunk.accept("\n\n");
        claudeClient.chatStream(
                withMemo(buildVerifierSystem(language), memo),
                buildVerifierUser(code, improved, analysis, language),
                TOKENS_VERIFIER,
                onChunk);
    }

    // ── 개선 코드 추출 (UI 호환) ───────────────────────────────────────────────

    /**
     * "## 🔧 개선된 코드" 섹션의 코드 펜스 안 내용을 추출합니다.
     * {@link #analyze} 가 조립한 텍스트와 스트리밍 파싱 모두에서 사용됩니다.
     */
    public String extractImprovedCode(String response, String language) {
        String[] markers = {"## 🔧 개선된 코드", "## 개선된 코드"};
        int sectionIdx = -1;
        for (String m : markers) {
            int idx = response.indexOf(m);
            if (idx >= 0) { sectionIdx = idx; break; }
        }
        String searchIn  = sectionIdx >= 0 ? response.substring(sectionIdx) : response;
        String langTag   = "sql".equalsIgnoreCase(language) ? "```sql" : "```java";
        int start = searchIn.indexOf(langTag);
        if (start == -1) start = searchIn.indexOf("```");
        if (start == -1) return "";
        int codeStart = searchIn.indexOf("\n", start);
        if (codeStart == -1) return "";
        codeStart = codeStart + 1;
        int end = searchIn.indexOf("```", codeStart);
        if (end == -1) return searchIn.substring(codeStart).trim();
        return searchIn.substring(codeStart, end).trim();
    }

    // ── 시스템 프롬프트 ────────────────────────────────────────────────────────

    private String buildAnalystSystem(String language) {
        boolean isSql   = "sql".equalsIgnoreCase(language);
        String langName = isSql ? "Oracle SQL" : "Java/Spring";
        return "당신은 " + langName + " 코드 분석 전문가(Analyst)입니다.\n"
             + "입력된 코드를 꼼꼼히 검토하여 성능 문제, 안티패턴, 가독성 문제, "
             + "보안 취약점, 개선 가능 지점을 파악하세요.\n"
             + "발견된 문제점을 항목 목록(- )으로 간결하게 출력하세요. "
             + "헤더나 전문(前文) 없이 목록부터 바로 시작하세요. 응답은 한국어로 작성하세요.";
    }

    private String buildBuilderSystem(String language) {
        boolean isSql   = "sql".equalsIgnoreCase(language);
        String langName = isSql ? "Oracle SQL" : "Java/Spring";
        return "당신은 " + langName + " 코드 개선 전문가(Builder)입니다.\n"
             + "분석 결과에서 지적된 모든 문제를 해결한 완성된 개선 코드를 작성하세요.\n"
             + "원본의 의도와 기능을 유지하면서 품질을 높이세요.\n"
             + "반드시 코드만 출력하세요. 코드 펜스(```)나 설명 없이 순수 코드만 출력하세요.";
    }

    private String buildReviewerSystem(String language) {
        boolean isSql   = "sql".equalsIgnoreCase(language);
        String langName = isSql ? "Oracle SQL" : "Java/Spring";
        return "당신은 " + langName + " 코드 검토자(Reviewer)입니다.\n"
             + "원본 코드, 분석 결과, 개선된 코드를 비교하여 변경 내역을 검증하고 최종 판정을 내립니다.\n"
             + "반드시 아래 4개 섹션 형식으로만 응답하세요:\n\n"
             + "## 📝 변경 내역\n"
             + "[각 변경 사항과 이유를 항목 목록(- )으로]\n\n"
             + "## 📈 기대 효과\n"
             + "[성능·가독성·유지보수성·보안 측면 개선 효과를 항목 목록(- )으로]\n\n"
             + "## 📊 품질 점수\n"
             + "[가독성: X/10, 성능: X/10, 유지보수성: X/10, 보안: X/10, 종합: X/10]\n"
             + "각 항목에 대한 1줄 평가를 제공하세요.\n\n"
             + "## ✅ 최종 검토 의견\n"
             + "[종합 판정(APPROVED / NEEDS_REVISION), 심각도, 주의 사항]\n\n"
             + "응답은 한국어로 작성하세요.";
    }

    private String buildVerifierSystem(String language) {
        boolean isSql = "sql".equalsIgnoreCase(language);
        if (isSql) {
            return "당신은 Oracle SQL 코드 검증 전문가(Verifier)입니다.\n"
                 + "개선된 SQL 코드가 실제 운영 환경에서 사용 가능한지를 정적 분석 관점에서 검증하세요.\n"
                 + "반드시 아래 4개 섹션 형식으로만 응답하세요:\n\n"
                 + "## 🛠 SQL 문법 검증\n"
                 + "[Oracle SQL 문법 오류, 잘못된 키워드 사용, 괄호·따옴표 불일치 등을 항목(- )으로 나열.\n"
                 + " 문제 없으면 '- 문법 오류 없음'으로 표기]\n\n"
                 + "## 🚨 위험 변경 감지\n"
                 + "[DROP TABLE/INDEX, TRUNCATE, WHERE 없는 DELETE/UPDATE, DDL 혼용 등을 항목(- )으로 나열.\n"
                 + " 발견된 경우 심각도(HIGH/MEDIUM/LOW) 표기. 없으면 '- 위험 변경 없음'으로 표기]\n\n"
                 + "## 🔗 Oracle 의존성 검증\n"
                 + "[Oracle 전용 함수·패키지(DBMS_*)·힌트·파티션 구문·시퀀스 등의 의존성을 항목(- )으로 나열.\n"
                 + " 의존성이 있으면 해당 항목이 개선 코드에서도 올바르게 유지되는지 확인]\n\n"
                 + "## 🏁 최종 검증 판정\n"
                 + "[한 줄 요약 후 반드시 아래 중 하나를 명시]\n"
                 + "**판정**: VERIFIED (문제 없음) | WARNINGS (주의 필요) | FAILED (심각한 문제)\n\n"
                 + "응답은 한국어로 작성하세요.";
        } else {
            return "당신은 Java/Spring 코드 검증 전문가(Verifier)입니다.\n"
                 + "개선된 Java 코드가 실제 운영 환경에서 컴파일·실행 가능한지를 정적 분석 관점에서 검증하세요.\n"
                 + "반드시 아래 4개 섹션 형식으로만 응답하세요:\n\n"
                 + "## 🛠 컴파일 가능성\n"
                 + "[import 누락, 타입 불일치, 미사용 변수, 접근 제어자 오류, 문법 오류 등을 항목(- )으로 나열.\n"
                 + " 문제 없으면 '- 컴파일 오류 없음'으로 표기]\n\n"
                 + "## 🚨 위험 변경 감지\n"
                 + "[메서드 시그니처 변경(하위 호환 깨짐), public API 제거, @Transactional 범위 축소,\n"
                 + " NullPointerException 위험 추가, 리소스 누수(Connection/Stream 미닫힘) 등을 항목(- )으로 나열.\n"
                 + " 발견된 경우 심각도(HIGH/MEDIUM/LOW) 표기. 없으면 '- 위험 변경 없음'으로 표기]\n\n"
                 + "## 🔗 Spring/JPA 호환성\n"
                 + "[Spring Bean 주입 방식, @Autowired 순환 의존, JPA N+1 쿼리 위험, Lazy 로딩 예외(LazyInitializationException),\n"
                 + " @Transactional 미적용 등 Spring/JPA 관련 호환성 문제를 항목(- )으로 나열.\n"
                 + " 없으면 '- 호환성 문제 없음'으로 표기]\n\n"
                 + "## 🏁 최종 검증 판정\n"
                 + "[한 줄 요약 후 반드시 아래 중 하나를 명시]\n"
                 + "**판정**: VERIFIED (문제 없음) | WARNINGS (주의 필요) | FAILED (심각한 문제)\n\n"
                 + "응답은 한국어로 작성하세요.";
        }
    }

    private String buildVerifierUser(String originalCode, String improvedCode,
                                     String analysis, String language) {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String langLabel = isSql ? "SQL"  : "Java";
        String codeBlock = isSql ? "sql"  : "java";
        return "다음 " + langLabel + " 코드 개선 결과를 검증하세요.\n\n"
             + "원본 코드:\n```" + codeBlock + "\n" + originalCode + "\n```\n\n"
             + "분석 결과 요약:\n" + analysis + "\n\n"
             + "개선된 코드:\n```" + codeBlock + "\n" + improvedCode + "\n```";
    }

    private String applyTemplateHint(String systemPrompt, String templateHint) {
        if (templateHint == null || templateHint.trim().isEmpty()) return systemPrompt;
        String hint;
        if ("performance".equals(templateHint))      hint = "특히 성능 최적화(인덱스, 쿼리 튜닝, 알고리즘 복잡도, 캐싱)에 집중하세요.";
        else if ("security".equals(templateHint))    hint = "특히 보안 취약점(SQL 인젝션, XSS, 인증·인가, 민감정보 노출)에 집중하세요.";
        else if ("refactoring".equals(templateHint)) hint = "특히 리팩터링(중복 코드 제거, 단일 책임 원칙, 디자인 패턴 적용)에 집중하세요.";
        else if ("sql_performance".equals(templateHint)) hint = "특히 Oracle SQL 성능(실행계획, 인덱스 활용, Full Table Scan 제거, 힌트)에 집중하세요.";
        else if ("readability".equals(templateHint)) hint = "특히 가독성·유지보수성(명확한 변수명, 주석, 함수 분리, 일관성)에 집중하세요.";
        else hint = "";
        if (hint.isEmpty()) return systemPrompt;
        return systemPrompt + "\n\n[분석 템플릿 집중 영역]\n" + hint;
    }

    // ── 사용자 메시지 ─────────────────────────────────────────────────────────

    private String buildAnalystUser(String code, String language) {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String langLabel = isSql ? "SQL"  : "Java";
        String codeBlock = isSql ? "sql"  : "java";
        return "다음 " + langLabel + " 코드의 문제점을 분석하세요:\n\n"
             + "```" + codeBlock + "\n" + code + "\n```";
    }

    private String buildBuilderUser(String code, String analysis, String language) {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String langLabel = isSql ? "SQL"  : "Java";
        String codeBlock = isSql ? "sql"  : "java";
        return "다음 " + langLabel + " 코드를 아래 분석 결과를 반영하여 개선된 코드를 작성하세요.\n\n"
             + "원본 코드:\n```" + codeBlock + "\n" + code + "\n```\n\n"
             + "분석 결과:\n" + analysis;
    }

    private String buildReviewerUser(String code, String improved,
                                     String analysis, String language) {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String langLabel = isSql ? "SQL"  : "Java";
        String codeBlock = isSql ? "sql"  : "java";
        return "다음 " + langLabel + " 코드 개선 결과를 검토하세요.\n\n"
             + "원본 코드:\n```" + codeBlock + "\n" + code + "\n```\n\n"
             + "분석 결과:\n" + analysis + "\n\n"
             + "개선된 코드:\n```" + codeBlock + "\n" + improved + "\n```";
    }

    // ── 유틸리티 ──────────────────────────────────────────────────────────────

    /** 시스템 프롬프트에 프로젝트 컨텍스트 메모를 추가합니다. */
    private String withMemo(String systemPrompt, String memo) {
        if (memo != null && !memo.trim().isEmpty()) {
            return systemPrompt + "\n\n[프로젝트 컨텍스트]\n" + memo;
        }
        return systemPrompt;
    }

    /**
     * Builder가 지시를 어기고 코드 펜스를 포함해 응답했을 경우 펜스를 제거합니다.
     * 정상적으로 순수 코드만 왔으면 그대로 반환합니다.
     */
    private String stripCodeFences(String text, String language) {
        if (text == null || text.isEmpty()) return text;
        String langTag = "sql".equalsIgnoreCase(language) ? "```sql" : "```java";
        int start = text.indexOf(langTag);
        if (start == -1) start = text.indexOf("```");
        if (start == -1) return text; // 펜스 없음 — 그대로 반환
        int codeStart = text.indexOf('\n', start);
        if (codeStart == -1) return text;
        codeStart = codeStart + 1;
        int end = text.indexOf("```", codeStart);
        if (end == -1) return text.substring(codeStart).trim();
        return text.substring(codeStart, end).trim();
    }
}
