package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.email.EmailService;
import io.github.claudetoolkit.ui.prompt.PromptService;
import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisServiceRegistry;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceHistory;
import io.github.claudetoolkit.ui.workspace.WorkspaceHistoryRepository;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 통합 워크스페이스 컨트롤러 (/workspace)
 *
 * <ul>
 *   <li>GET  /workspace                  — 워크스페이스 페이지</li>
 *   <li>GET  /workspace/analysis-types   — AnalysisType 목록 JSON</li>
 *   <li>POST /workspace/run              — 복수 분석 병렬 실행, streamId 맵 반환</li>
 *   <li>GET  /workspace/stream/{id}      — SSE 스트리밍 (timeout 180s)</li>
 *   <li>POST /workspace/compare          — 동일 코드 복수 모델 비교 분석</li>
 * </ul>
 */
@Controller
@RequestMapping("/workspace")
public class WorkspaceController {

    private static final int SSE_TIMEOUT_MS = 180_000;

    private final AnalysisServiceRegistry       registry;
    private final PromptService                 promptService;
    private final ClaudeClient                  claudeClient;
    private final ToolkitSettings               settings;
    private final EmailService                  emailService;
    private final WorkspaceHistoryRepository    historyRepo;

    /** streamId → SseEmitter (GET /workspace/stream/{id} 대기용) */
    private final ConcurrentHashMap<String, SseEmitter>       emitters =
            new ConcurrentHashMap<String, SseEmitter>();
    /** streamId → 스트리밍 파라미터 (POST /workspace/run 등록 → GET /stream 소비) */
    private final ConcurrentHashMap<String, WorkspaceRequest> pending  =
            new ConcurrentHashMap<String, WorkspaceRequest>();
    /** streamId → 타겟 모델 (모델 비교 전용) */
    private final ConcurrentHashMap<String, String>           models   =
            new ConcurrentHashMap<String, String>();

    public WorkspaceController(AnalysisServiceRegistry registry,
                               PromptService promptService,
                               ClaudeClient claudeClient,
                               ToolkitSettings settings,
                               EmailService emailService,
                               WorkspaceHistoryRepository historyRepo) {
        this.registry      = registry;
        this.promptService = promptService;
        this.claudeClient  = claudeClient;
        this.settings      = settings;
        this.emailService  = emailService;
        this.historyRepo   = historyRepo;
    }

    // ── 페이지 ─────────────────────────────────────────────────────────────────

    @GetMapping
    public String index(Model model) {
        model.addAttribute("analysisTypes", AnalysisType.values());
        return "workspace/index";
    }

    // ── 분석 유형 목록 JSON ────────────────────────────────────────────────────

    @GetMapping("/analysis-types")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> analysisTypes() {
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (AnalysisType type : AnalysisType.values()) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("type",               type.name());
            m.put("displayName",        type.displayName);
            m.put("description",        type.description);
            m.put("supportedLanguages", type.supportedLanguages);
            list.add(m);
        }
        return ResponseEntity.ok(list);
    }

    // ── 병렬 분석 실행 ─────────────────────────────────────────────────────────

    /**
     * 선택된 분석 유형들을 병렬로 실행합니다.
     *
     * @param code          분석 대상 코드
     * @param language      언어 식별자
     * @param selectedTypes 실행할 분석 유형 목록
     * @return { "CODE_REVIEW": "uuid1", "SQL_REVIEW": "uuid2", ... }
     */
    @PostMapping("/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> run(
            @RequestParam("code")                                  String code,
            @RequestParam("language")                              String language,
            @RequestParam(value = "selectedTypes", required = false) List<String> selectedTypes,
            @RequestParam(value = "projectContext", defaultValue = "") String projectContext,
            @RequestParam(value = "sourceName",    defaultValue = "") String sourceName) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (selectedTypes == null || selectedTypes.isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "분석 유형을 하나 이상 선택하세요.");
            return ResponseEntity.ok(resp);
        }

        Map<String, String> streamIds = new LinkedHashMap<String, String>();
        String memo = projectContext.isEmpty() ? settings.getProjectContext() : projectContext;

        for (String typeName : selectedTypes) {
            AnalysisType type;
            try { type = AnalysisType.valueOf(typeName.toUpperCase()); }
            catch (IllegalArgumentException e) { continue; }

            AnalysisService svc = registry.find(type);
            if (svc == null || !type.supports(language)) continue;

            String streamId = UUID.randomUUID().toString();
            WorkspaceRequest req = new WorkspaceRequest(code, language, type, memo);
            pending.put(streamId, req);
            streamIds.put(typeName, streamId);
        }

        if (!streamIds.isEmpty()) {
            String snippet = code.length() > 500 ? code.substring(0, 500) : code;
            String types   = String.join(",", streamIds.keySet());
            String src     = sourceName.trim().isEmpty() ? null : sourceName.trim();
            try { historyRepo.save(new WorkspaceHistory(language, types, snippet, src)); }
            catch (Exception ignored) {}
        }

        resp.put("success",   true);
        resp.put("streamIds", streamIds);
        return ResponseEntity.ok(resp);
    }

    // ── 분석 이력 조회 ─────────────────────────────────────────────────────────

    @GetMapping("/history")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> history() {
        List<WorkspaceHistory> rows = historyRepo.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, 30));
        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (WorkspaceHistory h : rows) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("id",            h.getId());
            m.put("language",      h.getLanguage());
            m.put("analysisTypes", h.getAnalysisTypes());
            m.put("codeSnippet",   h.getCodeSnippet());
            m.put("sourceName",    h.getSourceName());
            m.put("createdAt",     h.getCreatedAt().toString());
            list.add(m);
        }
        return ResponseEntity.ok(list);
    }

    // ── SSE 스트리밍 ───────────────────────────────────────────────────────────

    @GetMapping(value = "/stream/{streamId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@PathVariable String streamId) {
        SseEmitter emitter = new SseEmitter((long) SSE_TIMEOUT_MS);
        emitters.put(streamId, emitter);

        WorkspaceRequest req = pending.remove(streamId);
        if (req == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("스트림 정보를 찾을 수 없습니다."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        AnalysisService svc = registry.find(req.getAnalysisType());
        if (svc == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("분석 서비스를 찾을 수 없습니다."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        final String sysPrompt = promptService.resolveSystemPrompt(svc, req);
        final String userMsg   = svc.buildUserMessage(req);
        final int    maxTokens = claudeClient.getProperties().getMaxTokens();

        CompletableFuture.runAsync(new Runnable() {
            public void run() {
                try {
                    claudeClient.chatStream(sysPrompt, userMsg, maxTokens,
                            new Consumer<String>() {
                                public void accept(String chunk) {
                                    try { emitter.send(SseEmitter.event().data(chunk)); }
                                    catch (IOException e) { emitter.completeWithError(e); }
                                }
                            });
                    emitter.send(SseEmitter.event().name("done").data(""));
                    emitter.complete();
                } catch (Exception e) {
                    try {
                        emitter.send(SseEmitter.event().name("error").data(
                                e.getMessage() != null ? e.getMessage() : "분석 중 오류 발생"));
                        emitter.complete();
                    } catch (IOException ignored) {}
                }
            }
        });

        return emitter;
    }

    // ── 모델 비교 분석 ─────────────────────────────────────────────────────────

    /**
     * 동일 코드를 복수 모델로 병렬 분석합니다.
     *
     * @param code         분석 대상 코드
     * @param language     언어 식별자
     * @param analysisType 분석 유형
     * @param modelList    비교할 모델 ID 목록 (최대 4개)
     * @return { "claude-sonnet-4-5": "uuid1", "claude-opus-4-5": "uuid2" }
     */
    @PostMapping("/compare")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> compare(
            @RequestParam("code")         String code,
            @RequestParam("language")     String language,
            @RequestParam("analysisType") String analysisType,
            @RequestParam("models")       List<String> modelList,
            @RequestParam(value = "projectContext", defaultValue = "") String projectContext) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (modelList == null || modelList.isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "모델을 하나 이상 선택하세요.");
            return ResponseEntity.ok(resp);
        }

        AnalysisType type;
        try { type = AnalysisType.valueOf(analysisType.toUpperCase()); }
        catch (IllegalArgumentException e) {
            resp.put("success", false);
            resp.put("error",   "알 수 없는 분석 유형: " + analysisType);
            return ResponseEntity.ok(resp);
        }

        AnalysisService svc = registry.find(type);
        if (svc == null) {
            resp.put("success", false);
            resp.put("error",   "분석 서비스를 찾을 수 없습니다.");
            return ResponseEntity.ok(resp);
        }

        String memo = projectContext.isEmpty() ? settings.getProjectContext() : projectContext;
        Map<String, String> streamIds = new LinkedHashMap<String, String>();

        for (String model : modelList) {
            String streamId = UUID.randomUUID().toString();
            WorkspaceRequest req = new WorkspaceRequest(code, language, type, memo);
            pending.put(streamId, req);
            models.put(streamId, model.trim());
            streamIds.put(model.trim(), streamId);
        }

        resp.put("success",   true);
        resp.put("streamIds", streamIds);
        return ResponseEntity.ok(resp);
    }

    // ── 이메일 발송 ───────────────────────────────────────────────────────────────

    @PostMapping("/send-email")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> sendEmail(
            @RequestParam("to")                                   String to,
            @RequestParam(value = "subject", defaultValue = "")   String subject,
            @RequestParam("content")                              String content) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (to == null || to.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "수신자 이메일을 입력하세요.");
            return ResponseEntity.ok(resp);
        }
        if (!settings.isEmailConfigured()) {
            resp.put("success", false);
            resp.put("error",   "이메일 설정이 구성되지 않았습니다. Settings > Email 을 확인하세요.");
            return ResponseEntity.ok(resp);
        }
        String subj = (subject == null || subject.trim().isEmpty())
                ? "워크스페이스 분석 결과" : subject.trim();
        try {
            emailService.sendJobResult(to.trim(), subj, content);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage() != null ? e.getMessage() : "이메일 발송 실패");
        }
        return ResponseEntity.ok(resp);
    }

    /** 모델 비교용 SSE — 지정 모델로 실행 */
    @GetMapping(value = "/compare-stream/{streamId}", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter compareStream(@PathVariable String streamId) {
        SseEmitter emitter = new SseEmitter((long) SSE_TIMEOUT_MS);

        WorkspaceRequest req   = pending.remove(streamId);
        String           model = models.remove(streamId);

        if (req == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("스트림 정보를 찾을 수 없습니다."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        AnalysisService svc = registry.find(req.getAnalysisType());
        if (svc == null) {
            try {
                emitter.send(SseEmitter.event().name("error").data("분석 서비스를 찾을 수 없습니다."));
                emitter.complete();
            } catch (IOException ignored) {}
            return emitter;
        }

        final String sysPrompt  = promptService.resolveSystemPrompt(svc, req);
        final String userMsg    = svc.buildUserMessage(req);
        final int    maxTokens  = claudeClient.getProperties().getMaxTokens();
        final String targetModel = model;

        CompletableFuture.runAsync(new Runnable() {
            public void run() {
                String prevModel = claudeClient.getModel();
                try {
                    if (targetModel != null && !targetModel.isEmpty()) {
                        claudeClient.setModelOverride(targetModel);
                    }
                    claudeClient.chatStream(sysPrompt, userMsg, maxTokens,
                            new Consumer<String>() {
                                public void accept(String chunk) {
                                    try { emitter.send(SseEmitter.event().data(chunk)); }
                                    catch (IOException e) { emitter.completeWithError(e); }
                                }
                            });
                    emitter.send(SseEmitter.event().name("done").data(""));
                    emitter.complete();
                } catch (Exception e) {
                    try {
                        emitter.send(SseEmitter.event().name("error").data(
                                e.getMessage() != null ? e.getMessage() : "분석 중 오류 발생"));
                        emitter.complete();
                    } catch (IOException ignored) {}
                } finally {
                    // 모델 원복
                    claudeClient.setModelOverride(prevModel);
                }
            }
        });

        return emitter;
    }
}
