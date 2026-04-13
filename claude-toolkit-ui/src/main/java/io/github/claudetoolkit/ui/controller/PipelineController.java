package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.pipeline.*;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.security.Principal;
import java.util.*;

/**
 * 분석 파이프라인 오케스트레이터 컨트롤러 (v2.9.5).
 *
 * <p>경로:
 * <ul>
 *   <li>GET  /pipelines                            — 목록 페이지</li>
 *   <li>GET  /pipelines/new                        — 신규 편집기</li>
 *   <li>GET  /pipelines/{id}                       — 편집기</li>
 *   <li>POST /pipelines                            — 생성</li>
 *   <li>POST /pipelines/{id}/save                  — 수정</li>
 *   <li>POST /pipelines/{id}/delete                — 삭제</li>
 *   <li>POST /pipelines/{id}/validate              — YAML 검증 (저장 전)</li>
 *   <li>POST /pipelines/{id}/run                   — 실행 시작 → executionId 반환</li>
 *   <li>GET  /pipelines/executions/{id}            — 실행 상세 페이지</li>
 *   <li>GET  /pipelines/executions/{id}/stream     — SSE 실시간</li>
 *   <li>GET  /pipelines/executions/{id}/data       — 상태 JSON</li>
 * </ul>
 */
@Controller
@RequestMapping("/pipelines")
public class PipelineController {

    private final PipelineDefinitionRepository definitionRepo;
    private final PipelineExecutionRepository  executionRepo;
    private final PipelineStepResultRepository stepResultRepo;
    private final PipelineYamlParser           yamlParser;
    private final PipelineExecutor             executor;
    private final PipelineStreamBroker         broker;

    public PipelineController(PipelineDefinitionRepository definitionRepo,
                              PipelineExecutionRepository executionRepo,
                              PipelineStepResultRepository stepResultRepo,
                              PipelineYamlParser yamlParser,
                              PipelineExecutor executor,
                              PipelineStreamBroker broker) {
        this.definitionRepo = definitionRepo;
        this.executionRepo  = executionRepo;
        this.stepResultRepo = stepResultRepo;
        this.yamlParser     = yamlParser;
        this.executor       = executor;
        this.broker         = broker;
    }

    // ── 목록 페이지 ───────────────────────────────────────────────────────

    @GetMapping
    public String listPage(Model model, Principal principal) {
        List<PipelineDefinition> builtins = definitionRepo.findByIsBuiltinOrderByCreatedAtAsc(true);
        List<PipelineDefinition> userDefs  = definitionRepo.findByIsBuiltinOrderByCreatedAtAsc(false);
        model.addAttribute("builtins", builtins);
        model.addAttribute("userDefs", userDefs);
        return "pipelines/list";
    }

    // ── 편집기 ────────────────────────────────────────────────────────────

    @GetMapping("/new")
    public String newEditor(Model model) {
        PipelineDefinition blank = new PipelineDefinition("", "", DEFAULT_YAML, "java", false, null);
        model.addAttribute("def", blank);
        model.addAttribute("isNew", true);
        model.addAttribute("analysisTypes", AnalysisType.values());
        return "pipelines/editor";
    }

    @GetMapping("/{id}")
    public String editor(@PathVariable Long id, Model model) {
        PipelineDefinition def = definitionRepo.findById(id).orElse(null);
        if (def == null) {
            model.addAttribute("errorMessage", "파이프라인을 찾을 수 없습니다.");
            return "error";
        }
        model.addAttribute("def", def);
        model.addAttribute("isNew", false);
        model.addAttribute("analysisTypes", AnalysisType.values());
        return "pipelines/editor";
    }

    // ── 생성 ──────────────────────────────────────────────────────────────

    @PostMapping
    @ResponseBody
    public ResponseEntity<Map<String, Object>> create(
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam String yamlContent,
            @RequestParam(required = false, defaultValue = "java") String inputLanguage,
            Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            String validationError = yamlParser.validate(yamlContent);
            if (validationError != null) {
                resp.put("success", false);
                resp.put("error", "YAML 오류: " + validationError);
                return ResponseEntity.ok(resp);
            }
            PipelineDefinition def = new PipelineDefinition(name, description, yamlContent,
                    inputLanguage, false, principal.getName());
            definitionRepo.save(def);
            resp.put("success", true);
            resp.put("id", def.getId());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 수정 ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false, defaultValue = "") String description,
            @RequestParam String yamlContent,
            @RequestParam(required = false, defaultValue = "java") String inputLanguage) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            PipelineDefinition def = definitionRepo.findById(id).orElse(null);
            if (def == null) {
                resp.put("success", false);
                resp.put("error", "파이프라인을 찾을 수 없습니다.");
                return ResponseEntity.ok(resp);
            }
            if (def.isBuiltin()) {
                resp.put("success", false);
                resp.put("error", "내장 파이프라인은 수정할 수 없습니다.");
                return ResponseEntity.ok(resp);
            }
            String validationError = yamlParser.validate(yamlContent);
            if (validationError != null) {
                resp.put("success", false);
                resp.put("error", "YAML 오류: " + validationError);
                return ResponseEntity.ok(resp);
            }
            def.setName(name);
            def.setDescription(description);
            def.setYamlContent(yamlContent);
            def.setInputLanguage(inputLanguage);
            def.touch();
            definitionRepo.save(def);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 삭제 ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            PipelineDefinition def = definitionRepo.findById(id).orElse(null);
            if (def == null) {
                resp.put("success", false);
                resp.put("error", "파이프라인을 찾을 수 없습니다.");
                return ResponseEntity.ok(resp);
            }
            if (def.isBuiltin()) {
                resp.put("success", false);
                resp.put("error", "내장 파이프라인은 삭제할 수 없습니다.");
                return ResponseEntity.ok(resp);
            }
            definitionRepo.delete(def);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── YAML 검증 ─────────────────────────────────────────────────────────

    @PostMapping("/validate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> validate(@RequestParam String yamlContent) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        String error = yamlParser.validate(yamlContent);
        if (error != null) {
            resp.put("success", false);
            resp.put("error", error);
        } else {
            try {
                PipelineSpec spec = yamlParser.parse(yamlContent);
                resp.put("success", true);
                resp.put("stepCount", spec.getSteps().size());
                resp.put("name", spec.getName());
            } catch (Exception e) {
                resp.put("success", false);
                resp.put("error", e.getMessage());
            }
        }
        return ResponseEntity.ok(resp);
    }

    // ── 실행 ──────────────────────────────────────────────────────────────

    @PostMapping("/{id}/run")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> run(@PathVariable Long id,
                                                    @RequestParam String input,
                                                    Principal principal) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            if (input == null || input.trim().isEmpty()) {
                resp.put("success", false);
                resp.put("error", "입력 코드를 입력해주세요.");
                return ResponseEntity.ok(resp);
            }
            PipelineExecution exec = executor.start(id, input, principal.getName());
            resp.put("success", true);
            resp.put("executionId", exec.getId());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 실행 상세 페이지 ──────────────────────────────────────────────────

    @GetMapping("/executions/{id}")
    public String executionPage(@PathVariable Long id, Model model, Principal principal) {
        PipelineExecution exec = executionRepo.findById(id).orElse(null);
        if (exec == null) {
            model.addAttribute("errorMessage", "실행 이력을 찾을 수 없습니다.");
            return "error";
        }
        List<PipelineStepResult> steps = stepResultRepo.findByExecutionIdOrderByStepOrderAsc(id);
        model.addAttribute("exec", exec);
        model.addAttribute("steps", steps);
        return "pipelines/execution";
    }

    // ── SSE 스트림 ────────────────────────────────────────────────────────

    @GetMapping(value = "/executions/{id}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter executionStream(@PathVariable Long id) {
        return broker.subscribe(id);
    }

    // ── 상태 JSON ─────────────────────────────────────────────────────────

    @GetMapping("/executions/{id}/data")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> executionData(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        PipelineExecution exec = executionRepo.findById(id).orElse(null);
        if (exec == null) {
            resp.put("success", false);
            return ResponseEntity.ok(resp);
        }
        resp.put("success", true);
        resp.put("id",              exec.getId());
        resp.put("pipelineName",    exec.getPipelineName());
        resp.put("status",          exec.getStatus());
        resp.put("totalSteps",      exec.getTotalSteps());
        resp.put("completedSteps",  exec.getCompletedSteps());
        resp.put("progressPercent", exec.getProgressPercent());
        resp.put("errorMessage",    exec.getErrorMessage());

        List<PipelineStepResult> steps = stepResultRepo.findByExecutionIdOrderByStepOrderAsc(id);
        List<Map<String, Object>> stepList = new ArrayList<Map<String, Object>>();
        for (PipelineStepResult s : steps) {
            Map<String, Object> m = new LinkedHashMap<String, Object>();
            m.put("stepId",       s.getStepId());
            m.put("stepOrder",    s.getStepOrder());
            m.put("analysisType", s.getAnalysisType());
            m.put("status",       s.getStatus());
            m.put("outputContent", s.getOutputContent());
            m.put("skipReason",   s.getSkipReason());
            m.put("errorMessage", s.getErrorMessage());
            m.put("durationMs",   s.getDurationMs());
            stepList.add(m);
        }
        resp.put("steps", stepList);
        return ResponseEntity.ok(resp);
    }

    // ── v3.0: 스케줄 설정 ───────────────────────────────────────────────

    @PostMapping("/{id}/schedule")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setSchedule(
            @PathVariable Long id,
            @RequestParam(required = false) String cron,
            @RequestParam(required = false) String scheduleInput,
            @RequestParam(defaultValue = "false") boolean enabled) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            PipelineDefinition def = definitionRepo.findById(id).orElse(null);
            if (def == null) {
                resp.put("success", false);
                resp.put("error", "파이프라인을 찾을 수 없습니다.");
                return ResponseEntity.ok(resp);
            }
            // cron 검증
            if (cron != null && !cron.trim().isEmpty()) {
                try {
                    org.springframework.scheduling.support.CronExpression.parse(cron.trim());
                } catch (IllegalArgumentException e) {
                    resp.put("success", false);
                    resp.put("error", "잘못된 cron 표현식: " + e.getMessage());
                    return ResponseEntity.ok(resp);
                }
            }
            def.setScheduleCron(cron != null && !cron.trim().isEmpty() ? cron.trim() : null);
            def.setScheduleInput(scheduleInput);
            def.setScheduleEnabled(enabled);
            def.touch();
            definitionRepo.save(def);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 기본 YAML 템플릿 ──────────────────────────────────────────────────

    private static final String DEFAULT_YAML =
            "id: my-pipeline\n" +
            "name: 새 파이프라인\n" +
            "description: 파이프라인 설명\n" +
            "inputLanguage: java\n" +
            "\n" +
            "steps:\n" +
            "  - id: review\n" +
            "    analysis: CODE_REVIEW\n" +
            "    input: ${pipeline.input}\n" +
            "\n" +
            "  - id: refactor\n" +
            "    analysis: REFACTOR\n" +
            "    input: ${pipeline.input}\n" +
            "    context: ${review.output}\n";
}
