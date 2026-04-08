package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.prompt.CustomPrompt;
import io.github.claudetoolkit.ui.prompt.CustomPromptRepository;
import io.github.claudetoolkit.ui.prompt.PromptService;
import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisServiceRegistry;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 커스텀 시스템 프롬프트 관리 컨트롤러 (/settings/prompts)
 *
 * <ul>
 *   <li>GET  /settings/prompts                       — 프롬프트 관리 페이지</li>
 *   <li>GET  /settings/prompts/default/{type}        — 기본 내장 프롬프트 조회 JSON</li>
 *   <li>POST /settings/prompts/save                  — 커스텀 프롬프트 저장 & 활성화</li>
 *   <li>POST /settings/prompts/{id}/activate         — 특정 프롬프트 활성화</li>
 *   <li>POST /settings/prompts/{id}/deactivate       — 특정 프롬프트 비활성화</li>
 *   <li>POST /settings/prompts/reset/{type}          — 기본 프롬프트로 초기화</li>
 *   <li>DELETE /settings/prompts/{id}                — 프롬프트 삭제</li>
 * </ul>
 */
@Controller
@RequestMapping("/settings/prompts")
public class PromptController {

    private final PromptService              promptService;
    private final CustomPromptRepository     repository;
    private final AnalysisServiceRegistry    registry;

    public PromptController(PromptService promptService,
                            CustomPromptRepository repository,
                            AnalysisServiceRegistry registry) {
        this.promptService = promptService;
        this.repository    = repository;
        this.registry      = registry;
    }

    // ── 페이지 ─────────────────────────────────────────────────────────────────

    @GetMapping
    public String index(Model model) {
        // 분석 유형 목록 + 각 유형의 활성 프롬프트 여부
        List<Map<String, Object>> typeInfoList = new ArrayList<Map<String, Object>>();
        for (AnalysisType type : AnalysisType.values()) {
            Map<String, Object> info = new LinkedHashMap<String, Object>();
            info.put("type",         type.name());
            info.put("displayName",  type.displayName);
            info.put("description",  type.description);
            info.put("hasCustom",    repository.findByAnalysisTypeAndIsActiveTrue(type.name()).isPresent());
            typeInfoList.add(info);
        }
        model.addAttribute("typeInfoList", typeInfoList);
        return "settings/prompts";
    }

    // ── 기본 내장 프롬프트 조회 ────────────────────────────────────────────────

    @GetMapping("/default/{type}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDefault(@PathVariable String type) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            AnalysisType analysisType = AnalysisType.valueOf(type.toUpperCase());
            AnalysisService svc = registry.find(analysisType);
            if (svc == null) {
                resp.put("success", false);
                resp.put("error", "지원하지 않는 분석 유형입니다: " + type);
                return ResponseEntity.ok(resp);
            }
            WorkspaceRequest dummy = new WorkspaceRequest("", "java", analysisType, "");
            String defaultPrompt = svc.buildSystemPrompt(dummy);

            // 커스텀 프롬프트가 있으면 함께 반환
            Optional<CustomPrompt> custom =
                    repository.findByAnalysisTypeAndIsActiveTrue(type.toUpperCase());

            resp.put("success",       true);
            resp.put("defaultPrompt", defaultPrompt);
            resp.put("customPrompt",  custom.isPresent() ? custom.get().getSystemPrompt() : null);
            resp.put("customId",      custom.isPresent() ? custom.get().getId()           : null);
            resp.put("promptName",    custom.isPresent() ? custom.get().getPromptName()   : null);
            resp.put("hasCustom",     custom.isPresent());
        } catch (IllegalArgumentException e) {
            resp.put("success", false);
            resp.put("error", "알 수 없는 유형: " + type);
        }
        return ResponseEntity.ok(resp);
    }

    // ── 저장 & 활성화 ─────────────────────────────────────────────────────────

    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> save(
            @RequestParam("analysisType")  String analysisType,
            @RequestParam("promptName")    String promptName,
            @RequestParam("systemPrompt")  String systemPrompt) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            CustomPrompt saved = promptService.saveAndActivate(
                    analysisType.toUpperCase(), promptName, systemPrompt);
            resp.put("success",    true);
            resp.put("id",         saved.getId());
            resp.put("promptName", saved.getPromptName());
            resp.put("updatedAt",  saved.getFormattedUpdatedAt());
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 활성화 / 비활성화 ─────────────────────────────────────────────────────

    @PostMapping("/{id}/activate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> activate(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            promptService.activate(id);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/{id}/deactivate")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> deactivate(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            promptService.deactivate(id);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 초기화 (기본 프롬프트 복원) ──────────────────────────────────────────

    @PostMapping("/reset/{type}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> reset(@PathVariable String type) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            promptService.resetToDefault(type.toUpperCase());
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    // ── 삭제 ─────────────────────────────────────────────────────────────────

    @DeleteMapping("/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable Long id) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            promptService.delete(id);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
