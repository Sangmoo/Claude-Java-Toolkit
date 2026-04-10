package io.github.claudetoolkit.ui.pipeline;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
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

    public PipelineExecutor(PipelineDefinitionRepository definitionRepo,
                            PipelineExecutionRepository executionRepo,
                            PipelineStepResultRepository stepResultRepo,
                            PipelineYamlParser yamlParser,
                            PipelineExpressionEvaluator expressionEvaluator,
                            PipelineStreamBroker broker,
                            AnalysisServiceRegistry registry,
                            PromptService promptService,
                            ClaudeClient claudeClient,
                            ToolkitSettings settings) {
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

            for (int i = 0; i < spec.getSteps().size(); i++) {
                PipelineStepSpec step = spec.getSteps().get(i);
                PipelineStepResult result = stepResults.get(i);

                // 조건 평가
                if (step.getCondition() != null && !step.getCondition().trim().isEmpty()) {
                    boolean shouldRun = expressionEvaluator.evaluate(step.getCondition(), ctx);
                    if (!shouldRun) {
                        result.markSkipped("조건 미충족: " + step.getCondition());
                        stepResultRepo.save(result);
                        ctx.set(step.getId() + ".executed", false);
                        ctx.set(step.getId() + ".status", "SKIPPED");
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

                    // 스트리밍 실행
                    final StringBuilder outputBuf = new StringBuilder();
                    final String stepIdCopy = step.getId();
                    claudeClient.chatStream(sysPrompt, userMsg, maxTokens, new Consumer<String>() {
                        public void accept(String chunk) {
                            outputBuf.append(chunk);
                            Map<String, Object> payload = new LinkedHashMap<String, Object>();
                            payload.put("stepId", stepIdCopy);
                            payload.put("chunk", chunk);
                            broker.push(exec.getId(), "step-chunk", payload);
                        }
                    });

                    String output = outputBuf.toString();
                    result.markCompleted(output);
                    stepResultRepo.save(result);
                    ctx.set(step.getId() + ".output", output);
                    ctx.set(step.getId() + ".executed", true);
                    ctx.set(step.getId() + ".status", "COMPLETED");

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
}
