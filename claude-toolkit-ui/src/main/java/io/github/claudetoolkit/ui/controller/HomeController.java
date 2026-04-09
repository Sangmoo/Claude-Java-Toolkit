package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.favorites.FavoriteService;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import io.github.claudetoolkit.ui.security.AuditLogService;
import io.github.claudetoolkit.ui.user.AppUserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Controller
public class HomeController {

    private final FavoriteService      favoriteService;
    private final ReviewHistoryService historyService;
    private final ClaudeClient         claudeClient;
    private final ToolkitSettings      settings;
    private final AuditLogService      auditLogService;
    private final AppUserRepository    userRepository;

    public HomeController(FavoriteService favoriteService,
                          ReviewHistoryService historyService,
                          ClaudeClient claudeClient,
                          ToolkitSettings settings,
                          AuditLogService auditLogService,
                          AppUserRepository userRepository) {
        this.favoriteService = favoriteService;
        this.historyService  = historyService;
        this.claudeClient    = claudeClient;
        this.settings        = settings;
        this.auditLogService = auditLogService;
        this.userRepository  = userRepository;
    }

    @GetMapping("/")
    public String index(Model model) {
        // Recent 5 favorites
        List<io.github.claudetoolkit.ui.favorites.Favorite> allFavs = favoriteService.findAll();
        model.addAttribute("recentFavorites", allFavs.size() > 5 ? allFavs.subList(0, 5) : allFavs);
        model.addAttribute("favCount", allFavs.size());

        // Recent 5 history
        List<ReviewHistory> allHistory = historyService.findAll();
        model.addAttribute("recentHistory", allHistory.size() > 5 ? allHistory.subList(0, 5) : allHistory);
        model.addAttribute("historyCount", historyService.count());

        // ── 시스템 상태 위젯 ──
        model.addAttribute("currentModel", claudeClient.getEffectiveModel());
        model.addAttribute("apiKeySet", claudeClient.getApiKey() != null && !claudeClient.getApiKey().isEmpty());
        model.addAttribute("dbConfigured", settings.isDbConfigured());
        model.addAttribute("emailConfigured", settings.isEmailConfigured());

        // 이번 달 사용량
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        long monthTokens = 0; long monthRequests = 0;
        for (ReviewHistory h : allHistory) {
            if (h.getCreatedAt().isAfter(monthStart)) {
                monthRequests++;
                if (h.getInputTokens() != null) monthTokens += h.getInputTokens();
                if (h.getOutputTokens() != null) monthTokens += h.getOutputTokens();
            }
        }
        model.addAttribute("monthRequests", monthRequests);
        model.addAttribute("monthTokens", monthTokens);

        // 오늘 API 호출
        model.addAttribute("todayApiCalls", auditLogService.countToday());

        // 등록 사용자 수
        try { model.addAttribute("userCount", userRepository.count()); }
        catch (Exception e) { model.addAttribute("userCount", 0); }

        return "index";
    }
}
