package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.email.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 분석 결과 이메일 발송 통합 컨트롤러 (v4.2.x).
 *
 * <p>워크스페이스/파이프라인/코드 리뷰 하네스 등 모든 분석 메뉴에서 공유하는
 * 다수 수신자 이메일 발송 엔드포인트. 한 번 호출에 최대 10명까지 발송 가능.
 */
@Controller
@RequestMapping("/email")
public class EmailController {

    private static final Logger log = LoggerFactory.getLogger(EmailController.class);
    private static final int MAX_RECIPIENTS = 10;

    private final EmailService    emailService;
    private final ToolkitSettings settings;

    public EmailController(EmailService emailService, ToolkitSettings settings) {
        this.emailService = emailService;
        this.settings     = settings;
    }

    /**
     * 분석 결과를 다수 수신자에게 발송.
     * <p>{@code to} 는 쉼표/세미콜론/줄바꿈으로 구분된 이메일 목록 (최대 10명).
     */
    @PostMapping("/send")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> send(
            @RequestParam("to")                                  String to,
            @RequestParam(value = "subject", defaultValue = "")  String subject,
            @RequestParam("content")                             String content) {

        Map<String, Object> resp = new LinkedHashMap<String, Object>();

        if (!settings.isEmailConfigured()) {
            resp.put("success", false);
            resp.put("error",   "이메일 SMTP 설정이 구성되지 않았습니다. 설정 페이지에서 SMTP 정보를 입력해주세요.");
            return ResponseEntity.ok(resp);
        }
        if (to == null || to.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "수신자 이메일을 1명 이상 입력해주세요.");
            return ResponseEntity.ok(resp);
        }
        if (content == null || content.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "발송할 본문이 비어 있습니다.");
            return ResponseEntity.ok(resp);
        }

        // 쉼표/세미콜론/공백/줄바꿈으로 분리, 중복/빈 값 제거
        List<String> recipients = new ArrayList<String>();
        for (String r : to.split("[,;\\s]+")) {
            String t = r.trim();
            if (!t.isEmpty() && isValidEmail(t) && !recipients.contains(t)) {
                recipients.add(t);
            }
            if (recipients.size() >= MAX_RECIPIENTS) break;
        }
        if (recipients.isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "유효한 이메일 주소가 없습니다.");
            return ResponseEntity.ok(resp);
        }

        String subj = (subject == null || subject.trim().isEmpty())
                ? "Claude Toolkit 분석 결과" : subject.trim();

        int success = 0;
        List<String> failures = new ArrayList<String>();
        for (String rcpt : recipients) {
            try {
                emailService.sendJobResult(rcpt, subj, content);
                success++;
            } catch (Exception e) {
                failures.add(rcpt + ": " + (e.getMessage() != null ? e.getMessage() : "발송 실패"));
                log.warn("[EmailController] 발송 실패 → {}: {}", rcpt, e.getMessage());
            }
        }

        resp.put("success",       failures.isEmpty());
        resp.put("sentCount",     success);
        resp.put("totalCount",    recipients.size());
        resp.put("recipients",    recipients);
        if (!failures.isEmpty()) {
            resp.put("error",     "일부 수신자 발송 실패: " + String.join("; ", failures));
        }
        return ResponseEntity.ok(resp);
    }

    /** SMTP 설정 테스트 — 자기 자신(from) 으로 테스트 메일 발송 */
    @PostMapping("/test")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> test(
            @RequestParam(value = "to", defaultValue = "") String to) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        if (!settings.isEmailConfigured()) {
            resp.put("success", false);
            resp.put("error",   "이메일 SMTP 설정이 비어있습니다. 호스트/포트/사용자/비밀번호를 먼저 저장하세요.");
            return ResponseEntity.ok(resp);
        }
        String target = (to != null && !to.trim().isEmpty())
                ? to.trim()
                : (settings.getEmail().getFrom() != null && !settings.getEmail().getFrom().isEmpty()
                        ? settings.getEmail().getFrom()
                        : settings.getEmail().getUsername());
        if (target == null || target.isEmpty() || !isValidEmail(target)) {
            resp.put("success", false);
            resp.put("error",   "유효한 수신 이메일이 없습니다.");
            return ResponseEntity.ok(resp);
        }
        try {
            emailService.sendJobResult(target,
                    "[Claude Toolkit] SMTP 테스트 메일",
                    "이 메일이 보인다면 SMTP 설정이 정상입니다.\n발송 시각: "
                            + java.time.LocalDateTime.now());
            resp.put("success", true);
            resp.put("sentTo",  target);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage() != null ? e.getMessage() : "발송 실패");
        }
        return ResponseEntity.ok(resp);
    }

    private boolean isValidEmail(String s) {
        if (s == null) return false;
        return s.matches("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    }
}
