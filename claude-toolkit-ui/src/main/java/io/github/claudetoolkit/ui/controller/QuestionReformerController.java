package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * v4.2.8 — D3: 질문 리포머.
 *
 * <p>사용자의 모호한 입력을 Claude 가 먼저 검토해서 명료화 옵션을 제안한다.
 * 초보 사용자의 질문 품질 향상 + 토큰 낭비 감소 목적.
 *
 * <p>사용 흐름:
 *  1. 분석 페이지에서 사용자가 입력창에 질문 입력
 *  2. "🪄 질문 다듬기" 버튼 클릭
 *  3. Claude 가 "다음 중 어느 부분이 궁금하신가요? 1) ... 2) ..." 형식의 제안 반환
 *  4. 사용자가 옵션을 선택하거나 수정 후 본격 분석 시작
 *
 * <p>짧은 응답만 필요하므로 max_tokens 는 작게(512) 설정.
 */
@RestController
@RequestMapping("/api/v1/reformer")
public class QuestionReformerController {

    private static final Logger log = LoggerFactory.getLogger(QuestionReformerController.class);

    private static final String SYSTEM_PROMPT =
        "당신은 개발자 질문을 더 명확하게 다듬는 코치입니다.\n\n" +
        "입력된 질문이 모호하거나 여러 해석이 가능하다면, 가능한 구체적인 관점을 3~4개 제시하세요.\n" +
        "각 제안은 번호(1), 2), 3), 4))와 한 문장 설명으로 출력하세요.\n\n" +
        "질문이 이미 충분히 구체적이라면 '✅ 질문이 이미 구체적입니다' 라고 답하고 그대로 진행하도록 안내하세요.\n\n" +
        "형식 예시:\n" +
        "이 질문은 여러 관점에서 해석할 수 있어요. 어느 쪽을 원하시는지 골라주세요:\n\n" +
        "1) 성능 관점 — 실행 속도 / 메모리 사용량\n" +
        "2) 보안 관점 — 인젝션 / 권한 체크\n" +
        "3) 가독성 관점 — 네이밍 / 구조\n" +
        "4) 호환성 관점 — Oracle 버전 / Java 8 호환\n\n" +
        "응답은 한국어로 간결하게 작성하세요. 200 단어 이내.";

    private static final int MAX_TOKENS = 512;

    private final ClaudeClient claudeClient;

    public QuestionReformerController(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    @PostMapping("/refine")
    public ResponseEntity<Map<String, Object>> refine(@RequestParam("input") String input) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (input == null || input.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "입력 내용이 비어 있습니다.");
            return ResponseEntity.ok(resp);
        }
        try {
            String userMessage = "다음 질문을 검토하고 더 구체화가 필요한지 판단해주세요:\n\n" + input.trim();
            String suggestions = claudeClient.chat(SYSTEM_PROMPT, userMessage, MAX_TOKENS);
            resp.put("success",     true);
            resp.put("suggestions", suggestions);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.warn("[Reformer] failed: {}", e.getMessage());
            resp.put("success", false);
            resp.put("error",   "질문 리포머 호출 실패: " + e.getMessage());
            return ResponseEntity.ok(resp);
        }
    }
}
