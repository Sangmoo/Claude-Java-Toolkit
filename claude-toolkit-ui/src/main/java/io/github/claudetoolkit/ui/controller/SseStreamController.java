package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.harness.HarnessReviewService;
import io.github.claudetoolkit.ui.translate.SqlTranslateService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Generic SSE (Server-Sent Events) streaming endpoint.
 *
 * <p>Two-step flow:
 * <ol>
 *   <li>POST /stream/init  — stores input params, returns a one-time {@code streamId}</li>
 *   <li>GET  /stream/{id}  — opens an SSE channel, streams Claude response text</li>
 * </ol>
 *
 * <p>Supported feature keys:
 * {@code sql_review}, {@code sql_security}, {@code doc_gen}, {@code code_review},
 * {@code test_gen}, {@code api_spec}, {@code log_analysis}, {@code log_security},
 * {@code regex_gen}, {@code commit_msg}
 */
@Controller
@RequestMapping("/stream")
public class SseStreamController {

    /** Pending inputs awaiting streaming — keyed by UUID. Auto-expire in 5 min. */
    private final ConcurrentHashMap<String, StreamInput> pending =
            new ConcurrentHashMap<String, StreamInput>();

    private final ClaudeClient         claudeClient;
    private final ToolkitSettings      settings;
    private final HarnessReviewService harnessService;
    private final SqlTranslateService  translateService;

    public SseStreamController(ClaudeClient claudeClient,
                               ToolkitSettings settings,
                               HarnessReviewService harnessService,
                               SqlTranslateService translateService) {
        this.claudeClient     = claudeClient;
        this.settings         = settings;
        this.harnessService   = harnessService;
        this.translateService = translateService;
    }

    // ── Direct registration (for internal use, no HTTP round-trip) ──────────

    /**
     * Register a stream directly (bypasses HTTP). Returns the streamId.
     * Used by ExplainPlanController for streaming AI analysis after DB phase.
     */
    public String registerStream(String feature, String input, String input2, String sourceType) {
        final String id = java.util.UUID.randomUUID().toString();
        pending.put(id, new StreamInput(feature, input, input2, sourceType));
        Thread cleaner = new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(300_000); } catch (InterruptedException ignored) {}
                pending.remove(id);
            }
        });
        cleaner.setDaemon(true);
        cleaner.start();
        return id;
    }

    // ── Step 1: Store input ───────────────────────────────────────────────────

    @PostMapping("/init")
    @ResponseBody
    public String initStream(
            @RequestParam(value = "feature",    defaultValue = "")        String feature,
            @RequestParam(value = "input",      defaultValue = "")        String input,
            @RequestParam(value = "input2",     defaultValue = "")        String input2,
            @RequestParam(value = "sourceType", defaultValue = "General") String sourceType) {

        final String id = java.util.UUID.randomUUID().toString();
        pending.put(id, new StreamInput(feature, input, input2, sourceType));

        // Auto-expire
        Thread cleaner = new Thread(new Runnable() {
            public void run() {
                try { Thread.sleep(300_000); } catch (InterruptedException ignored) {}
                pending.remove(id);
            }
        });
        cleaner.setDaemon(true);
        cleaner.start();

        return id;
    }

    // ── Step 2: Stream response ───────────────────────────────────────────────

    @GetMapping(value = "/{id}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String id) {
        final SseEmitter emitter = new SseEmitter(180_000L);
        final StreamInput input  = pending.remove(id);

        if (input == null) {
            try { emitter.send(SseEmitter.event().name("error_msg").data("Invalid or expired stream ID")); }
            catch (IOException ignored) {}
            emitter.complete();
            return emitter;
        }

        Thread thread = new Thread(new Runnable() {
            public void run() {
                try {
                    // ── 하네스: Analyst→Builder→Reviewer 3단계 분리 스트리밍 ────────────
                    if ("harness_review".equals(input.feature)) {
                        harnessService.analyzeStream(
                                input.input,
                                input.sourceType,  // sourceType에 language("java"/"sql")가 담김
                                input.input2,      // input2에 templateHint가 담김
                                new Consumer<String>() {
                                    public void accept(String chunk) {
                                        try { emitter.send(SseEmitter.event().data(chunk)); }
                                        catch (IOException e) { emitter.completeWithError(e); }
                                    }
                                });
                        emitter.send(SseEmitter.event().name("done").data("ok"));
                        emitter.complete();
                        return;
                    }

                    // ── SQL 번역: sourceType=sourceDb, input2=targetDb ─────────────────
                    if ("sql_translate".equals(input.feature)) {
                        // input2 = sourceDb, sourceType = targetDb (SqlTranslateController에서 매핑)
                        String sourceDb = input.input2;
                        String targetDb = input.sourceType;
                        String sysPrompt = translateService.buildSystemPrompt(sourceDb, targetDb);
                        String memo = settings.getProjectContext();
                        if (memo != null && !memo.trim().isEmpty()) {
                            sysPrompt = sysPrompt + "\n\n[프로젝트 컨텍스트]\n" + memo;
                        }
                        String userMsg = translateService.buildUserMessage(input.input, sourceDb, targetDb);
                        claudeClient.chatStream(sysPrompt, userMsg,
                                claudeClient.getProperties().getMaxTokens(),
                                new Consumer<String>() {
                                    public void accept(String chunk) {
                                        try { emitter.send(SseEmitter.event().data(chunk)); }
                                        catch (IOException e) { emitter.completeWithError(e); }
                                    }
                                });
                        emitter.send(SseEmitter.event().name("done").data("ok"));
                        emitter.complete();
                        return;
                    }

                    // ── 그 외 기능: 단일 스트리밍 호출 ───────────────────────────────────
                    String systemPrompt = resolveSystemPrompt(input.feature, input.sourceType);
                    String memoContext  = settings.getProjectContext();
                    if (memoContext != null && !memoContext.trim().isEmpty()) {
                        systemPrompt = systemPrompt + "\n\n[프로젝트 컨텍스트]\n" + memoContext;
                    }
                    String userMessage = buildUserMessage(input.feature, input.input, input.input2, input.sourceType);

                    claudeClient.chatStream(systemPrompt, userMessage,
                            claudeClient.getProperties().getMaxTokens(),
                            new Consumer<String>() {
                                public void accept(String chunk) {
                                    try { emitter.send(SseEmitter.event().data(chunk)); }
                                    catch (IOException e) { emitter.completeWithError(e); }
                                }
                            });
                    emitter.send(SseEmitter.event().name("done").data("ok"));
                    emitter.complete();
                } catch (Exception e) {
                    try { emitter.send(SseEmitter.event().name("error_msg").data(
                            e.getMessage() != null ? e.getMessage() : "streaming error")); }
                    catch (IOException ignored) {}
                    emitter.completeWithError(e);
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
        return emitter;
    }

    // ── System prompt resolver ────────────────────────────────────────────────

    private String resolveSystemPrompt(String feature, String sourceType) {
        if ("sql_review".equals(feature)) {
            return "당신은 Oracle SQL 전문가입니다. SQL 코드를 분석하여 성능 문제, 안티패턴, 개선 방안을 ## 리뷰 결과 형식으로 출력하세요. 각 항목은 [SEVERITY: HIGH/MEDIUM/LOW]로 표시하세요.";
        }
        if ("sql_security".equals(feature)) {
            return "당신은 데이터베이스 보안 전문가입니다. SQL 코드에서 SQL 인젝션, 권한 문제, 민감 데이터 노출 등 보안 취약점을 ## 보안 감사 결과 형식으로 출력하세요. 각 항목은 [SEVERITY: HIGH/MEDIUM/LOW]로 표시하세요.";
        }
        if ("doc_gen".equals(feature)) {
            return "당신은 Spring Boot 기술 문서 작성 전문가입니다. " + sourceType + " 클래스를 분석하여 Markdown 기술 문서를 작성하세요.";
        }
        if ("code_review".equals(feature)) {
            return "당신은 Java 코드 리뷰 전문가입니다. ## 코드 품질, ## 버그 위험, ## 개선 제안, ## 우수한 점 형식으로 리뷰하세요. 각 항목은 [SEVERITY: HIGH/MEDIUM/LOW]로 표시하세요.";
        }
        if ("code_review_security".equals(feature)) {
            return "당신은 Java 보안 코드 리뷰 전문가입니다. OWASP Top 10 기준으로 보안 취약점을 분석하고 ## 보안 취약점, ## 위험도, ## 해결 방법 형식으로 출력하세요.";
        }
        if ("test_gen".equals(feature)) {
            return "당신은 JUnit 5 테스트 전문가입니다. 주어진 " + sourceType + " 클래스에 대한 완전한 JUnit 5 테스트 코드를 생성하세요.";
        }
        if ("api_spec".equals(feature)) {
            return "당신은 REST API 문서 전문가입니다. Spring Controller 코드를 분석하여 OpenAPI 3.0 스타일의 API 명세 문서를 Markdown 형식으로 작성하세요.";
        }
        if ("log_analysis".equals(feature)) {
            return "당신은 Spring Boot 로그 분석 전문가입니다. ## 오류 분석, ## 원인 파악, ## 해결 방법, ## 예방 방법 형식으로 분석하세요.";
        }
        if ("log_security".equals(feature)) {
            return "당신은 보안 로그 분석 전문가입니다. SQL 인젝션, XSS, 인증 오류 등 보안 위협을 탐지하고 ## 오류 분석, ## 원인 파악, ## 해결 방법, ## 예방 방법 형식으로 분석하세요.";
        }
        if ("regex_gen".equals(feature)) {
            return "당신은 정규식 전문가입니다. ## 정규식, ## 설명, ## " + sourceType.toUpperCase() + " 사용 예제, ## 테스트 케이스 형식으로 출력하세요.";
        }
        if ("commit_msg".equals(feature)) {
            return "당신은 Git 커밋 메시지 전문가입니다. Conventional Commits 형식으로 ## 추천 커밋 메시지, ## 대안 메시지 (3개), ## 변경 사항 요약 형식으로 출력하세요.";
        }
        if ("javadoc_gen".equals(feature)) {
            return "당신은 Java 전문가입니다. 주어진 Java 소스 코드에 Javadoc 주석을 추가하여 완전한 코드를 반환하세요. 모든 public 클래스, 메서드, 필드에 한국어 Javadoc을 작성하세요.";
        }
        if ("refactor_gen".equals(feature)) {
            return "당신은 Java 리팩터링 전문가입니다. 주어진 코드의 문제점을 분석하고 ## 현재 코드 문제점, ## 리팩터링 제안, ## 개선된 코드, ## 설명 형식으로 출력하세요.";
        }
        if ("index_opt".equals(feature)) {
            return "당신은 Oracle DBA 전문가입니다. 주어진 SQL 쿼리를 분석하여 인덱스 최적화 방안을 ## 현재 쿼리 분석, ## 추천 인덱스 (CREATE INDEX 구문 포함), ## 예상 성능 향상, ## 주의사항 형식으로 출력하세요.";
        }
        if ("explain_plan".equals(feature)) {
            return "당신은 Oracle DBA 전문가입니다. Oracle EXPLAIN PLAN 실행 계획을 분석하여 다음 형식으로 답변하세요.\n\n" +
                   "## 📊 실행 계획 요약\n전체 비용(Cost)과 주요 특징을 2~3줄로 요약.\n\n" +
                   "## 🔴 성능 이슈\n[SEVERITY: HIGH/MEDIUM/LOW] 이슈 설명 형식으로 목록 작성.\n" +
                   "예) TABLE ACCESS FULL이 발생하는 테이블, 높은 Cost 단계, Cartesian Join 등\n\n" +
                   "## 💡 최적화 제안\n구체적인 인덱스 생성 또는 쿼리 개선 방안. Oracle 11g/12c 호환 구문 사용.\n\n" +
                   "## 🌲 핵심 단계 해설\n가장 비용이 높은 2~3개 단계를 선택하여 왜 비용이 발생하는지 설명.\n\n응답은 한국어로 작성하세요.";
        }
        if ("sql_refactor".equals(feature)) {
            return "당신은 Oracle SQL 최적화 전문가입니다. 주어진 원본 SQL의 문제점을 분석하고 최적화된 SQL을 제안하세요.\n\n" +
                   "## 📋 원본 SQL 분석\n현재 SQL의 문제점과 개선이 필요한 부분을 설명.\n\n" +
                   "## 🔧 최적화된 SQL\n```sql\n-- 개선된 SQL 코드\n```\n\n" +
                   "## 📝 변경 사항 설명\n각 변경 사항과 그 이유를 항목별로 설명.\n\n" +
                   "## 📈 예상 효과\n성능 개선이 예상되는 근거와 주의사항.\n\n응답은 한국어로 작성하세요.";
        }
        if ("harness_review".equals(feature)) {
            boolean isSql    = "sql".equalsIgnoreCase(sourceType);
            String langName  = isSql ? "Oracle SQL"   : "Java/Spring";
            String codeBlock = isSql ? "sql"           : "java";
            return "당신은 " + langName + " 코드 품질 개선 파이프라인입니다.\n"
                 + "다음 3단계 하네스(Harness) 프로세스를 순서대로 실행하세요:\n\n"
                 + "**1단계 — 분석가(Analyst)**: 성능 문제, 안티패턴, 가독성 문제, 보안 취약점, 개선 가능 지점을 파악합니다.\n"
                 + "**2단계 — 개선가(Builder)**: 분석 결과를 토대로 모든 문제를 해결한 개선 코드를 작성합니다.\n"
                 + "**3단계 — 검토자(Reviewer)**: 변경점을 검증하고 변경 내역·기대 효과·최종 판정을 정리합니다.\n\n"
                 + "반드시 아래 형식으로만 응답하세요:\n\n"
                 + "## 📋 분석 요약\n[분석가: 문제점 항목 목록]\n\n"
                 + "## 🔧 개선된 코드\n```" + codeBlock + "\n[개선된 전체 코드]\n```\n\n"
                 + "## 📝 변경 내역\n[검토자: 변경 사항 항목 목록]\n\n"
                 + "## 📈 기대 효과\n[검토자: 성능·가독성·유지보수성 개선 효과]\n\n"
                 + "## ✅ 최종 검토 의견\n[검토자: APPROVED/NEEDS_REVISION 판정, 심각도, 주의 사항]";
        }
        return "당신은 Java/Spring 개발 전문가 어시스턴트입니다.";
    }

    private String buildUserMessage(String feature, String input, String input2, String sourceType) {
        if ("sql_review".equals(feature) || "sql_security".equals(feature)) {
            return "다음 SQL을 분석해주세요:\n\n```sql\n" + input + "\n```";
        }
        if ("doc_gen".equals(feature)) {
            return "다음 " + sourceType + " 코드에 대한 기술 문서를 생성해주세요:\n\n```java\n" + input + "\n```";
        }
        if ("code_review".equals(feature) || "code_review_security".equals(feature)) {
            return "다음 Java 코드를 리뷰해주세요:\n\n```java\n" + input + "\n```";
        }
        if ("test_gen".equals(feature)) {
            return "다음 " + sourceType + " 코드에 대한 JUnit 5 테스트를 생성해주세요:\n\n```java\n" + input + "\n```";
        }
        if ("api_spec".equals(feature)) {
            return "다음 Controller 코드에 대한 API 명세를 작성해주세요:\n\n```java\n" + input + "\n```";
        }
        if ("log_analysis".equals(feature) || "log_security".equals(feature)) {
            return "다음 로그를 분석해주세요:\n\n```\n" + input + "\n```";
        }
        if ("regex_gen".equals(feature)) {
            return "다음 요구사항에 맞는 정규식을 " + sourceType + " 언어로 생성해주세요:\n\n" + input;
        }
        if ("commit_msg".equals(feature)) {
            return "다음 코드 변경사항에 대한 커밋 메시지를 생성해주세요:\n\n```\n" + input + "\n```";
        }
        if ("javadoc_gen".equals(feature)) {
            return "다음 Java 소스 코드에 Javadoc 주석을 추가해주세요:\n\n```java\n" + input + "\n```";
        }
        if ("refactor_gen".equals(feature)) {
            return "다음 Java 코드를 리팩터링해주세요:\n\n```java\n" + input + "\n```";
        }
        if ("index_opt".equals(feature)) {
            return "다음 SQL 쿼리의 인덱스 최적화 방안을 제안해주세요:\n\n```sql\n" + input + "\n```";
        }
        if ("explain_plan".equals(feature)) {
            return "## SQL\n```sql\n" + input + "\n```\n\n## EXPLAIN PLAN\n```\n" + input2 + "\n```";
        }
        if ("sql_refactor".equals(feature)) {
            return "다음 SQL을 분석하고 최적화된 버전을 제안해주세요:\n\n```sql\n" + input + "\n```";
        }
        if ("harness_review".equals(feature)) {
            boolean isSql    = "sql".equalsIgnoreCase(sourceType);
            String langLabel = isSql ? "SQL"  : "Java";
            String codeBlock = isSql ? "sql"  : "java";
            return "다음 " + langLabel + " 코드를 3단계 하네스 파이프라인으로 분석하고 개선해주세요:\n\n"
                 + "```" + codeBlock + "\n" + input + "\n```";
        }
        return input;
    }

    // ── Inner DTO ─────────────────────────────────────────────────────────────

    private static class StreamInput {
        final String feature;
        final String input;
        final String input2;
        final String sourceType;

        StreamInput(String feature, String input, String input2, String sourceType) {
            this.feature    = feature;
            this.input      = input;
            this.input2     = input2;
            this.sourceType = sourceType;
        }
    }
}
