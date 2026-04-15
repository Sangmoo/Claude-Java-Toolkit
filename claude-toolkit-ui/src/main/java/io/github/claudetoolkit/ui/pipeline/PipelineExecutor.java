package io.github.claudetoolkit.ui.pipeline;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryRepository;
import io.github.claudetoolkit.ui.prompt.PromptService;
import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisServiceRegistry;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * 파이프라인 실행 엔진 (v2.9.5).
 *
 * <p>비동기 스레드에서 단계별로 순차 실행:
 * <ol>
 *   <li>YAML 파싱 → {@link PipelineSpec}</li>
 *   <li>{@link PipelineContext} 초기화 (input, language)</li>
 *   <li>각 step마다:
 *     <ul>
 *       <li>condition 평가 (PipelineExpressionEvaluator)</li>
 *       <li>input/context 변수 치환</li>
 *       <li>AnalysisService 찾아서 chatStream 호출</li>
 *       <li>결과를 DB + context에 저장, SSE push</li>
 *     </ul>
 *   </li>
 * </ol>
 */
@Service
public class PipelineExecutor {

    private static final Logger log = LoggerFactory.getLogger(PipelineExecutor.class);

    private final PipelineDefinitionRepository  definitionRepo;
    private final PipelineExecutionRepository   executionRepo;
    private final PipelineStepResultRepository  stepResultRepo;
    private final PipelineYamlParser            yamlParser;
    private final PipelineExpressionEvaluator   expressionEvaluator;
    private final PipelineStreamBroker          broker;
    private final AnalysisServiceRegistry       registry;
    private final PromptService                 promptService;
    private final ClaudeClient                  claudeClient;
    private final ToolkitSettings                settings;
    private final ReviewHistoryRepository       historyRepo;

    public PipelineExecutor(PipelineDefinitionRepository definitionRepo,
                            PipelineExecutionRepository executionRepo,
                            PipelineStepResultRepository stepResultRepo,
                            PipelineYamlParser yamlParser,
                            PipelineExpressionEvaluator expressionEvaluator,
                            PipelineStreamBroker broker,
                            AnalysisServiceRegistry registry,
                            PromptService promptService,
                            ClaudeClient claudeClient,
                            ToolkitSettings settings,
                            ReviewHistoryRepository historyRepo) {
        this.definitionRepo      = definitionRepo;
        this.executionRepo       = executionRepo;
        this.stepResultRepo      = stepResultRepo;
        this.yamlParser          = yamlParser;
        this.expressionEvaluator = expressionEvaluator;
        this.broker              = broker;
        this.registry            = registry;
        this.promptService       = promptService;
        this.claudeClient        = claudeClient;
        this.settings            = settings;
        this.historyRepo         = historyRepo;
    }

    /**
     * 파이프라인 실행 시작 — 동기적으로 실행 레코드를 생성한 후 백그라운드 스레드에서 단계를 처리합니다.
     * @return 생성된 {@link PipelineExecution} (실행 시작 직후 상태 RUNNING)
     */
    @Transactional
    public PipelineExecution start(Long pipelineId, String inputText, String username) {
        PipelineDefinition def = definitionRepo.findById(pipelineId).orElse(null);
        if (def == null) {
            throw new IllegalArgumentException("파이프라인을 찾을 수 없습니다: " + pipelineId);
        }

        // YAML 사전 검증
        PipelineSpec spec;
        try {
            spec = yamlParser.parse(def.getYamlContent());
        } catch (Exception e) {
            throw new IllegalArgumentException("파이프라인 YAML 오류: " + e.getMessage(), e);
        }

        // 실행 레코드 생성
        PipelineExecution exec = new PipelineExecution(def, inputText, username, spec.getSteps().size());
        executionRepo.save(exec);

        // 각 단계 초기 PENDING 상태로 저장
        for (int i = 0; i < spec.getSteps().size(); i++) {
            PipelineStepSpec step = spec.getSteps().get(i);
            PipelineStepResult result = new PipelineStepResult(
                    exec.getId(), i, step.getId(), step.getAnalysis());
            stepResultRepo.save(result);
        }

        // 백그라운드 실행
        final PipelineExecution finalExec = exec;
        final PipelineSpec finalSpec = spec;
        Thread thread = new Thread(new Runnable() {
            public void run() {
                runInBackground(finalExec, finalSpec);
            }
        });
        thread.setDaemon(true);
        thread.setName("pipeline-exec-" + exec.getId());
        thread.start();

        return exec;
    }

    /** 백그라운드 실행 로직 */
    private void runInBackground(PipelineExecution exec, PipelineSpec spec) {
        try {
            PipelineContext ctx = new PipelineContext();
            ctx.set("pipeline.input", exec.getInputText());
            ctx.set("pipeline.language", spec.getInputLanguage());

            java.util.List<PipelineStepResult> stepResults =
                    stepResultRepo.findByExecutionIdOrderByStepOrderAsc(exec.getId());

            // v3.0: 완료된 단계 추적 (병렬 단계 간 dependsOn 대기용)
            final java.util.Set<String> completedStepIds =
                    java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

            for (int i = 0; i < spec.getSteps().size(); i++) {
                PipelineStepSpec step = spec.getSteps().get(i);
                PipelineStepResult result = stepResults.get(i);

                // v3.0: dependsOn 대기 — 지정된 단계들이 완료될 때까지 폴링
                java.util.List<String> deps = step.getDependsOnList();
                if (!deps.isEmpty()) {
                    int waitMs = 0;
                    while (!completedStepIds.containsAll(deps) && waitMs < 600_000) {
                        try { Thread.sleep(500); waitMs += 500; } catch (InterruptedException ie) { break; }
                    }
                }

                // v3.0: parallel 단계는 별도 스레드에서 실행
                if (step.isParallel()) {
                    final int idx = i;
                    final PipelineStepSpec parallelStep = step;
                    final PipelineStepResult parallelResult = result;
                    final PipelineExecution execRef = exec;
                    final PipelineContext ctxRef = ctx;
                    Thread parallelThread = new Thread(new Runnable() {
                        public void run() {
                            try {
                                executeSingleStep(parallelStep, parallelResult, execRef, ctxRef, spec);
                                completedStepIds.add(parallelStep.getId());
                            } catch (Throwable e) {
                                parallelResult.markFailed(e.getMessage());
                                stepResultRepo.save(parallelResult);
                                broker.push(execRef.getId(), "error", buildStepPayload(parallelResult));
                            }
                        }
                    });
                    parallelThread.setDaemon(true);
                    parallelThread.setName("pipeline-parallel-" + step.getId());
                    parallelThread.start();
                    continue;  // 다음 단계로 바로 넘어감 (병렬)
                }

                // 조건 평가
                if (step.getCondition() != null && !step.getCondition().trim().isEmpty()) {
                    boolean shouldRun = expressionEvaluator.evaluate(step.getCondition(), ctx);
                    if (!shouldRun) {
                        result.markSkipped("조건 미충족: " + step.getCondition());
                        stepResultRepo.save(result);
                        ctx.set(step.getId() + ".executed", false);
                        ctx.set(step.getId() + ".status", "SKIPPED");
                        completedStepIds.add(step.getId());
                        exec.incrementCompletedSteps();
                        executionRepo.save(exec);
                        broker.push(exec.getId(), "step-skipped", buildStepPayload(result));
                        continue;
                    }
                }

                try {
                    result.markRunning();
                    stepResultRepo.save(result);
                    broker.push(exec.getId(), "step-start", buildStepPayload(result));

                    // 변수 치환
                    String stepInput = ctx.resolve(step.getInput());
                    String stepContext = step.getContext() != null ? ctx.resolve(step.getContext()) : null;
                    result.setInputContent(stepInput);
                    stepResultRepo.save(result);

                    // 분석 서비스 찾기
                    AnalysisType type;
                    try {
                        type = AnalysisType.valueOf(step.getAnalysis().toUpperCase());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("지원하지 않는 분석 유형: " + step.getAnalysis());
                    }
                    AnalysisService svc = registry.find(type);
                    if (svc == null) {
                        throw new IllegalArgumentException("분석 서비스를 찾을 수 없습니다: " + step.getAnalysis());
                    }

                    // WorkspaceRequest 구성
                    String lang = spec.getInputLanguage() != null ? spec.getInputLanguage() : "java";
                    WorkspaceRequest req = new WorkspaceRequest(stepInput, lang, type,
                            settings.getProjectContext());
                    if (stepContext != null) {
                        // WorkspaceRequest에 컨텍스트 별도 필드가 없으므로 projectContext에 합쳐서 전달
                        String combined = (settings.getProjectContext() != null && !settings.getProjectContext().isEmpty()
                                ? settings.getProjectContext() + "\n\n[이전 단계 결과]\n" + stepContext
                                : "[이전 단계 결과]\n" + stepContext);
                        req = new WorkspaceRequest(stepInput, lang, type, combined);
                    }

                    String sysPrompt = promptService.resolveSystemPrompt(svc, req);
                    String userMsg   = svc.buildUserMessage(req);
                    int    maxTokens = claudeClient.getProperties().getMaxTokens();

                    // ── 파이프라인 컨텍스트 전파 (v4.2.4 버그 수정) ────────────
                    // AnalysisService.buildUserMessage 구현체들이 request.getProjectContext()
                    // 를 읽지 않아 이전 단계 결과가 Claude 까지 도달하지 못함.
                    // stepContext(이전 단계 결과) 를 userMsg 앞에 직접 prepend 하여
                    // 연쇄 파이프라인이 실제로 단계 간 컨텍스트를 공유하도록 보장.
                    if (stepContext != null && !stepContext.trim().isEmpty()) {
                        userMsg = stepContext + "\n\n---\n\n" + userMsg;
                    }
                    log.info("[Pipeline] 단계 실행: exec={}, step={}, inputLen={}, contextLen={}",
                            exec.getId(), step.getId(),
                            stepInput == null ? 0 : stepInput.length(),
                            stepContext == null ? 0 : stepContext.length());

                    // 스트리밍 실행 — 진행적 DB 저장 (300ms 또는 1000자마다)
                    // v4.2.5: chatStreamWithContinuation 사용 — Claude 응답이 max_tokens
                    // 한도에 닿아 중간에 잘리면 자동으로 최대 3회 "이어서 작성해주세요" 로
                    // 다음 turn 을 요청해 받는다. 긴 코드 생성 단계에서 잘림 방지.
                    final StringBuilder outputBuf = new StringBuilder();
                    final String stepIdCopy = step.getId();
                    final long[] lastSave   = { System.currentTimeMillis() };
                    final int[]  lastLen    = { 0 };
                    final PipelineStepResult resultRef = result;
                    claudeClient.chatStreamWithContinuation(sysPrompt, userMsg, maxTokens, 3, new Consumer<String>() {
                        public void accept(String chunk) {
                            outputBuf.append(chunk);
                            Map<String, Object> payload = new LinkedHashMap<String, Object>();
                            payload.put("stepId", stepIdCopy);
                            payload.put("chunk", chunk);
                            // broker.push 가 동기적으로 던지는 IOException 은 자체 catch 에서
                            // 처리되지만, 비동기 dispatch 단계에서 발생하는 ClientAbortException 은
                            // GlobalExceptionHandler 가 흡수한다. 여기서 예외가 올라와도 청크 처리는
                            // 계속되도록 추가 try 로 감싼다.
                            try {
                                broker.push(exec.getId(), "step-chunk", payload);
                            } catch (Exception ignored) { /* 클라이언트 disconnect 무시 */ }

                            // 폴링 fallback 을 위한 진행적 저장
                            long now = System.currentTimeMillis();
                            int  len = outputBuf.length();
                            if (now - lastSave[0] > 300 || len - lastLen[0] > 1000) {
                                try {
                                    resultRef.setOutputContent(outputBuf.toString());
                                    stepResultRepo.save(resultRef);
                                    lastSave[0] = now;
                                    lastLen[0]  = len;
                                } catch (Exception ignored) {}
                            }
                        }
                    });

                    String output = outputBuf.toString();
                    // v4.2.4: 빈 응답 방어 — Claude 가 0 청크로 끝나면 사용자가 원인 파악 가능하도록
                    if (output == null || output.trim().isEmpty()) {
                        log.warn("[Pipeline] 단계가 빈 응답으로 종료됨: exec={}, step={} — 원인: Claude 응답 0 청크",
                                exec.getId(), step.getId());
                        output = "(AI 응답이 비어 있습니다. 입력이 너무 길거나 모델이 응답을 거부했을 수 있습니다. "
                               + "다른 단계 결과나 로그를 확인해 보세요.)";
                    }
                    log.info("[Pipeline] 단계 완료: exec={}, step={}, outputLen={}",
                            exec.getId(), step.getId(), output.length());
                    result.markCompleted(output);
                    stepResultRepo.save(result);
                    ctx.set(step.getId() + ".output", output);
                    ctx.set(step.getId() + ".executed", true);
                    ctx.set(step.getId() + ".status", "COMPLETED");

                    completedStepIds.add(step.getId());
                    exec.incrementCompletedSteps();
                    executionRepo.save(exec);
                    broker.push(exec.getId(), "step-completed", buildStepPayload(result));

                } catch (Throwable e) {
                    log.error("[Pipeline] 단계 실행 실패: executionId={}, step={}, error={}",
                            exec.getId(), step.getId(), e.getMessage(), e);
                    result.markFailed(e.getMessage() != null ? e.getMessage() : "알 수 없는 오류");
                    stepResultRepo.save(result);
                    exec.markFailed("Step '" + step.getId() + "' 실패: " + e.getMessage());
                    executionRepo.save(exec);
                    broker.push(exec.getId(), "error", buildStepPayload(result));
                    broker.closeAll(exec.getId());
                    return;
                }
            }

            exec.markCompleted();
            executionRepo.save(exec);
            // v4.2.5: 파이프라인 전체 실행 결과를 ReviewHistory 에 저장
            saveHistoryForExecution(exec, spec);
            broker.push(exec.getId(), "done", "ok");
            broker.closeAll(exec.getId());

        } catch (Throwable e) {
            log.error("[Pipeline] 실행 엔진 오류: executionId={}, error={}", exec.getId(), e.getMessage(), e);
            try {
                exec.markFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                executionRepo.save(exec);
                broker.push(exec.getId(), "error", e.getMessage());
                broker.closeAll(exec.getId());
            } catch (Exception ignored) {}
        }
    }

    private Map<String, Object> buildStepPayload(PipelineStepResult result) {
        Map<String, Object> m = new LinkedHashMap<String, Object>();
        m.put("stepId",       result.getStepId());
        m.put("stepOrder",    result.getStepOrder());
        m.put("analysisType", result.getAnalysisType());
        m.put("status",       result.getStatus());
        m.put("skipReason",   result.getSkipReason());
        m.put("errorMessage", result.getErrorMessage());
        m.put("durationMs",   result.getDurationMs());
        return m;
    }

    /**
     * v4.2.5: 파이프라인 전체 실행 결과를 ReviewHistory 에 저장.
     *
     * <p>파이프라인은 {@code /stream/init} 경로가 아닌 {@link ClaudeClient#chatStream}
     * 을 직접 호출하기 때문에 {@code SseStreamController.saveHistory()} 가 호출되지
     * 않아 리뷰 이력이 남지 않는 버그가 있었음. 파이프라인 완료 후 모든 단계 결과를
     * 하나의 마크다운 문서로 합쳐 ReviewHistory 에 저장한다.
     */
    private void saveHistoryForExecution(PipelineExecution exec, PipelineSpec spec) {
        try {
            java.util.List<PipelineStepResult> steps =
                    stepResultRepo.findByExecutionIdOrderByStepOrderAsc(exec.getId());

            StringBuilder combined = new StringBuilder();
            combined.append("# ").append(exec.getPipelineName()).append("\n\n");
            combined.append("**실행 ID**: ").append(exec.getId())
                    .append("  ·  **단계 수**: ").append(exec.getTotalSteps())
                    .append("  ·  **완료 단계**: ").append(exec.getCompletedSteps())
                    .append("\n\n---\n\n");
            for (PipelineStepResult s : steps) {
                combined.append("## [").append(s.getStepOrder() + 1).append("] ")
                        .append(s.getStepId())
                        .append(" — ").append(s.getAnalysisType())
                        .append(" (").append(s.getStatus()).append(")\n\n");
                if (s.getOutputContent() != null && !s.getOutputContent().trim().isEmpty()) {
                    combined.append(s.getOutputContent()).append("\n\n");
                } else if (s.getSkipReason() != null && !s.getSkipReason().isEmpty()) {
                    combined.append("_(건너뜀: ").append(s.getSkipReason()).append(")_\n\n");
                } else if (s.getErrorMessage() != null && !s.getErrorMessage().isEmpty()) {
                    combined.append("_(오류: ").append(s.getErrorMessage()).append(")_\n\n");
                } else {
                    combined.append("_(결과 없음)_\n\n");
                }
                combined.append("---\n\n");
            }

            String input     = exec.getInputText() != null ? exec.getInputText() : "";
            String output    = combined.toString();
            String type      = "PIPELINE";
            String title     = exec.getPipelineName() != null
                    ? exec.getPipelineName() + " (파이프라인)"
                    : "파이프라인 실행";
            if (title.length() > 60) title = title.substring(0, 57) + "...";

            ReviewHistory h = new ReviewHistory(type, title, input, output);
            h.setUsername(exec.getUsername());   // 실행자 — exec 엔티티에 저장되어 있음
            historyRepo.save(h);
            log.info("[Pipeline] ReviewHistory 저장 완료: exec={}, historyId={}, user={}",
                    exec.getId(), h.getId(), exec.getUsername());
        } catch (Exception e) {
            log.warn("[Pipeline] ReviewHistory 저장 실패: exec={}, error={}",
                    exec.getId(), e.getMessage());
        }
    }

    /**
     * v3.0: 단일 단계 실행 (병렬 스레드에서도 호출 가능).
     * 조건 평가는 호출 전에 완료된 상태.
     */
    private void executeSingleStep(PipelineStepSpec step, PipelineStepResult result,
                                   PipelineExecution exec, PipelineContext ctx,
                                   PipelineSpec spec) throws Exception {
        result.markRunning();
        stepResultRepo.save(result);
        broker.push(exec.getId(), "step-start", buildStepPayload(result));

        String stepInput = ctx.resolve(step.getInput());
        String stepContext = step.getContext() != null ? ctx.resolve(step.getContext()) : null;
        result.setInputContent(stepInput);
        stepResultRepo.save(result);

        io.github.claudetoolkit.ui.workspace.AnalysisType type =
                io.github.claudetoolkit.ui.workspace.AnalysisType.valueOf(step.getAnalysis().toUpperCase());
        AnalysisService svc = registry.find(type);
        if (svc == null) throw new IllegalArgumentException("분석 서비스 없음: " + step.getAnalysis());

        String lang = spec.getInputLanguage() != null ? spec.getInputLanguage() : "java";
        io.github.claudetoolkit.ui.workspace.WorkspaceRequest req =
                new io.github.claudetoolkit.ui.workspace.WorkspaceRequest(stepInput, lang, type,
                        stepContext != null ? stepContext : settings.getProjectContext());

        String sysPrompt = promptService.resolveSystemPrompt(svc, req);
        String userMsg   = svc.buildUserMessage(req);
        int    maxTokens = claudeClient.getProperties().getMaxTokens();

        // v4.2.4: 파이프라인 컨텍스트 전파 (AnalysisService 가 projectContext 무시하는 문제 대응)
        if (stepContext != null && !stepContext.trim().isEmpty()) {
            userMsg = stepContext + "\n\n---\n\n" + userMsg;
        }
        log.info("[Pipeline] 병렬 단계 실행: exec={}, step={}, inputLen={}, contextLen={}",
                exec.getId(), step.getId(),
                stepInput == null ? 0 : stepInput.length(),
                stepContext == null ? 0 : stepContext.length());

        final StringBuilder outputBuf = new StringBuilder();
        final String stepIdCopy = step.getId();
        final long[] lastSave   = { System.currentTimeMillis() };
        final int[]  lastLen    = { 0 };
        final PipelineStepResult resultRef = result;
        // v4.2.5: chatStreamWithContinuation — max_tokens 잘림 자동 이어받기 (최대 3회)
        claudeClient.chatStreamWithContinuation(sysPrompt, userMsg, maxTokens, 3, new Consumer<String>() {
            public void accept(String chunk) {
                outputBuf.append(chunk);
                Map<String, Object> payload = new LinkedHashMap<String, Object>();
                payload.put("stepId", stepIdCopy);
                payload.put("chunk", chunk);
                try {
                    broker.push(exec.getId(), "step-chunk", payload);
                } catch (Exception ignored) { /* 클라이언트 disconnect 무시 */ }

                // 폴링 fallback 을 위한 진행적 저장
                long now = System.currentTimeMillis();
                int  len = outputBuf.length();
                if (now - lastSave[0] > 300 || len - lastLen[0] > 1000) {
                    try {
                        resultRef.setOutputContent(outputBuf.toString());
                        stepResultRepo.save(resultRef);
                        lastSave[0] = now;
                        lastLen[0]  = len;
                    } catch (Exception ignored) {}
                }
            }
        });

        String output = outputBuf.toString();
        // v4.2.4: 빈 응답 방어 — Claude 가 0 청크로 끝나는 경우 사용자가 원인 파악 가능하도록
        if (output == null || output.trim().isEmpty()) {
            log.warn("[Pipeline] 단계가 빈 응답으로 종료됨: exec={}, step={} — 원인: Claude 응답 0 청크", exec.getId(), step.getId());
            output = "(AI 응답이 비어 있습니다. 입력이 너무 길거나 모델이 응답을 거부했을 수 있습니다. "
                   + "다른 단계 결과나 로그를 확인해 보세요.)";
        }
        result.markCompleted(output);
        stepResultRepo.save(result);
        ctx.set(step.getId() + ".output", output);
        ctx.set(step.getId() + ".executed", true);
        ctx.set(step.getId() + ".status", "COMPLETED");

        synchronized (exec) {
            exec.incrementCompletedSteps();
            executionRepo.save(exec);
        }
        broker.push(exec.getId(), "step-completed", buildStepPayload(result));
    }
}
