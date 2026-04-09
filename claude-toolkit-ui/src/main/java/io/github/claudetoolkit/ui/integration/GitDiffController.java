package io.github.claudetoolkit.ui.integration;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * Git 로컬 저장소 diff 분석 컨트롤러.
 */
@Controller
@RequestMapping("/git-diff")
public class GitDiffController {

    private final GitService gitService;
    private final ClaudeClient claudeClient;

    public GitDiffController(GitService gitService, ClaudeClient claudeClient) {
        this.gitService  = gitService;
        this.claudeClient = claudeClient;
    }

    @GetMapping
    public String index() { return "git-diff/index"; }

    /** 최근 커밋 목록 */
    @PostMapping("/commits")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getCommits(
            @RequestParam String repoPath,
            @RequestParam(defaultValue = "20") int limit) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            List<GitService.GitCommit> commits = gitService.getRecentCommits(repoPath, limit);
            List<Map<String, String>> list = new ArrayList<Map<String, String>>();
            for (GitService.GitCommit c : commits) {
                Map<String, String> m = new LinkedHashMap<String, String>();
                m.put("hash", c.hash); m.put("shortHash", c.shortHash);
                m.put("message", c.message); m.put("author", c.author);
                m.put("timestamp", c.timestamp);
                list.add(m);
            }
            resp.put("success", true);
            resp.put("commits", list);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 두 커밋 간 diff */
    @PostMapping("/diff")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> getDiff(
            @RequestParam String repoPath,
            @RequestParam String fromCommit,
            @RequestParam String toCommit) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            String diff = gitService.getDiff(repoPath, fromCommit, toCommit);
            resp.put("success", true);
            resp.put("diff", diff);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** diff를 Claude AI로 분석 */
    @PostMapping("/analyze")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> analyze(@RequestParam String diff) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            String prompt = "다음 Git diff를 코드 리뷰해주세요.\n"
                    + "## 분석 항목\n- 버그 위험\n- 성능 이슈\n- 리팩터링 제안\n- 보안 취약점\n\n"
                    + "각 항목에 [SEVERITY: HIGH/MEDIUM/LOW] 표시. 한국어 응답.\n\n"
                    + "```diff\n" + diff + "\n```";
            String result = claudeClient.chat("당신은 코드 리뷰 전문가입니다.", prompt);
            resp.put("success", true);
            resp.put("review", result);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
