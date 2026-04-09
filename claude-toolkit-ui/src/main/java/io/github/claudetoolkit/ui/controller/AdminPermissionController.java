package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.user.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 사용자별 프로그램 권한 관리 컨트롤러 (ADMIN 전용).
 */
@Controller
@RequestMapping("/admin/permissions")
public class AdminPermissionController {

    /** 관리 대상 기능 목록: {key, 표시명, 카테고리} */
    private static final List<String[]> FEATURES = Arrays.asList(
        // 분석
        // 분석
        new String[]{"workspace",    "통합 워크스페이스",   "분석"},
        new String[]{"advisor",      "SQL 리뷰",          "분석"},
        new String[]{"sql-translate","SQL DB 번역",       "분석"},
        new String[]{"sql-batch",    "배치 SQL 분석",      "분석"},
        new String[]{"erd",          "ERD 분석",          "분석"},
        new String[]{"complexity",   "복잡도 분석",        "분석"},
        new String[]{"explain",      "실행계획 분석/비교/대시보드", "분석"},
        new String[]{"harness",      "코드 리뷰 하네스/배치/의존성/대시보드", "분석"},
        new String[]{"codereview",   "코드 리뷰",         "분석"},
        // 생성
        new String[]{"docgen",       "기술 문서",          "생성"},
        new String[]{"testgen",      "테스트 생성",        "생성"},
        new String[]{"apispec",      "API 명세",          "생성"},
        new String[]{"converter",    "코드 변환",          "생성"},
        new String[]{"mockdata",     "Mock 데이터",       "생성"},
        new String[]{"migration",    "DB 마이그레이션",    "생성"},
        new String[]{"batch",        "Batch 처리",        "생성"},
        new String[]{"depcheck",     "의존성 분석 (pom)",  "생성"},
        new String[]{"migrate",      "Spring 마이그레이션", "생성"},
        // 기록
        new String[]{"history",      "리뷰 이력",          "기록"},
        new String[]{"favorites",    "즐겨찾기",           "기록"},
        new String[]{"usage",        "사용량 모니터링",     "기록"},
        new String[]{"roi-report",   "ROI 리포트",         "기록"},
        new String[]{"schedule",     "분석 스케줄링",       "기록"},
        // 도구
        new String[]{"loganalyzer",  "로그 분석기",        "도구"},
        new String[]{"regex",        "정규식 생성기",      "도구"},
        new String[]{"commitmsg",    "커밋 메시지",        "도구"},
        new String[]{"maskgen",      "마스킹 스크립트",    "도구"},
        new String[]{"input-masking","민감정보 마스킹",    "도구"},
        new String[]{"github-pr",    "GitHub PR 리뷰",    "도구"},
        new String[]{"git-diff",     "Git Diff 분석",     "도구"},
        // 기타
        new String[]{"prompts",      "프롬프트 템플릿",    "기타"},
        new String[]{"search",       "글로벌 검색",        "기타"}
    );

    private final AppUserRepository userRepository;
    private final UserPermissionRepository permissionRepository;

    public AdminPermissionController(AppUserRepository userRepository,
                                     UserPermissionRepository permissionRepository) {
        this.userRepository     = userRepository;
        this.permissionRepository = permissionRepository;
    }

    @GetMapping
    public String index(Model model) {
        model.addAttribute("users", userRepository.findAllByOrderByCreatedAtDesc());
        // features를 JSON 문자열로 전달 (Thymeleaf 인라인 파싱 문제 회피)
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < FEATURES.size(); i++) {
            String[] f = FEATURES.get(i);
            if (i > 0) json.append(",");
            json.append("{\"key\":\"").append(f[0]).append("\",\"name\":\"").append(f[1]).append("\",\"category\":\"").append(f[2]).append("\"}");
        }
        json.append("]");
        model.addAttribute("featuresJson", json.toString());
        return "admin/permissions";
    }

    /** 특정 사용자의 권한 목록 조회 */
    @GetMapping("/{userId}")
    @ResponseBody
    public ResponseEntity<Map<String, Boolean>> getUserPermissions(@PathVariable Long userId) {
        List<UserPermission> perms = permissionRepository.findByUserId(userId);
        Map<String, Boolean> result = new LinkedHashMap<String, Boolean>();
        // 기본값: 전체 허용
        for (String[] f : FEATURES) {
            result.put(f[0], true);
        }
        // DB에 저장된 값 오버라이드
        for (UserPermission p : perms) {
            result.put(p.getFeatureKey(), p.isAllowed());
        }
        return ResponseEntity.ok(result);
    }

    /** 특정 사용자의 권한 일괄 저장 */
    @PostMapping("/{userId}/save")
    @ResponseBody
    @Transactional
    public ResponseEntity<Map<String, Object>> savePermissions(
            @PathVariable Long userId,
            @RequestParam Map<String, String> params) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            for (String[] f : FEATURES) {
                String key = f[0];
                boolean allowed = "true".equals(params.get(key));
                UserPermission existing = permissionRepository.findByUserIdAndFeatureKey(userId, key).orElse(null);
                if (existing != null) {
                    existing.setAllowed(allowed);
                    permissionRepository.save(existing);
                } else {
                    permissionRepository.save(new UserPermission(userId, key, allowed));
                }
            }
            resp.put("success", true);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error", e.getMessage());
        }
        return ResponseEntity.ok(resp);
    }
}
