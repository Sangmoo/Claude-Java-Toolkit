package io.github.claudetoolkit.ui.integration;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub PR 자동 코멘트 컨트롤러.
 */
@Controller
@RequestMapping("/github-pr")
public class GitHubPrController {

    private static final Pattern PR_URL = Pattern.compile("github\\.com/([^/]+)/([^/]+)/pull/(\\d+)");

    private final GitHubService gitHubService;
    private final ClaudeClient claudeClient;

    public GitHubPrController(GitHubService gitHubService, ClaudeClient claudeClient) {
        this.gitHubService = gitHubService;
        this.claudeClient  = claudeClient;
    }

    @GetMapping
    public String index() { return "github-pr/index"; }

    /** PR diff + description 가져오기 */
    @PostMapping("/fetch")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> fetchPr(
            @RequestParam String prUrl,
            @RequestParam(defaultValue = "") String token) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            Matcher m = PR_URL.matcher(prUrl);
            if (!m.find()) { resp.put("success", false); resp.put("error", "PR URL 형식이 올바르지 않습니다."); return ResponseEntity.ok(resp); }
            String owner = m.group(1), repo = m.group(2);
            int prNumber = Integer.parseInt(m.group(3));
            String diff = gitHubService.fetchPrDiff(owner, repo, prNumber, token);
            String desc = gitHubService.fetchPrDescription(owner, repo, prNumber, token);
            resp.put("success", true);
            resp.put("owner", owner); resp.put("repo", repo); resp.put("prNumber", prNumber);
            resp.put("diff", diff); resp.put("description", desc);
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
            String prompt = "다음 GitHub PR diff를 분석하여 코드 리뷰를 수행하세요.\n"
                    + "## 리뷰 항목\n- 버그 위험\n- 성능 이슈\n- 보안 취약점\n- 코드 스타일\n\n"
                    + "각 항목에 [SEVERITY: HIGH/MEDIUM/LOW] 표시하세요.\n\n"
                    + "```diff\n" + diff + "\n```";
            String result = claudeClient.chat("당신은 코드 리뷰 전문가입니다. 한국어로 응답하세요.", prompt);
            resp.put("success", true);
            resp.put("review", result);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }

    /** 분석 결과를 PR 코멘트로 등록 */
    @PostMapping("/comment")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> postComment(
            @RequestParam String owner, @RequestParam String repo,
            @RequestParam int prNumber, @RequestParam String token,
            @RequestParam String comment) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            gitHubService.postComment(owner, repo, prNumber, token,
                    "## 🤖 Claude AI 코드 리뷰\n\n" + comment);
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
