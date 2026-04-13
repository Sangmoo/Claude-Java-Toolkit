package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.security.TotpService;
import io.github.claudetoolkit.ui.user.AppUser;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 로그인 후 2FA 검증 페이지 컨트롤러.
 */
@Controller
@RequestMapping("/login/2fa")
public class TwoFactorController {

    private final AppUserRepository userRepository;

    public TwoFactorController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping
    @ResponseBody
    public Object showPage(HttpSession session) {
        String username = (String) session.getAttribute("2fa_username");
        if (username == null) return "redirect:/login";

        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null) return "redirect:/login";

        if (!user.isTotpEnabled()) {
            String secret = TotpService.generateSecret();
            session.setAttribute("2fa_setup_secret", secret);
        }

        // React SPA 직접 서빙 (SpaForwardController의 캐시된 index.html 사용)
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(SpaForwardController.getIndexHtml());
    }

    /** OTP 코드 검증 (등록된 사용자) */
    @PostMapping("/verify")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> verify(
            @RequestParam String code, HttpSession session) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        String username = (String) session.getAttribute("2fa_username");
        if (username == null) { resp.put("success", false); resp.put("error", "세션 만료"); return ResponseEntity.ok(resp); }

        AppUser user = userRepository.findByUsername(username).orElse(null);
        if (user == null || !user.isTotpEnabled()) {
            resp.put("success", false); resp.put("error", "사용자 없음");
            return ResponseEntity.ok(resp);
        }

        if (TotpService.verifyCode(user.getTotpSecret(), code)) {
            session.removeAttribute("2fa_pending");
            session.removeAttribute("2fa_username");
            resp.put("success", true);
        } else {
            resp.put("success", false);
            resp.put("error", "인증 코드가 올바르지 않습니다.");
        }
        return ResponseEntity.ok(resp);
    }

    /** OTP 최초 등록 + 검증 (미등록 사용자) */
    @PostMapping("/setup")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> setup(
            @RequestParam String code, HttpSession session) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        String username = (String) session.getAttribute("2fa_username");
        String secret   = (String) session.getAttribute("2fa_setup_secret");
        if (username == null || secret == null) {
            resp.put("success", false); resp.put("error", "세션 만료");
            return ResponseEntity.ok(resp);
        }

        if (TotpService.verifyCode(secret, code)) {
            AppUser user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                user.setTotpSecret(secret);
                userRepository.save(user);
            }
            session.removeAttribute("2fa_pending");
            session.removeAttribute("2fa_username");
            session.removeAttribute("2fa_setup_secret");
            resp.put("success", true);
        } else {
            resp.put("success", false);
            resp.put("error", "인증 코드가 올바르지 않습니다. 다시 확인하세요.");
        }
        return ResponseEntity.ok(resp);
    }
}
