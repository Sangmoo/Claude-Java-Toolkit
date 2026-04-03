package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.favorites.FavoriteService;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final FavoriteService      favoriteService;
    private final ReviewHistoryService historyService;

    public HomeController(FavoriteService favoriteService,
                          ReviewHistoryService historyService) {
        this.favoriteService = favoriteService;
        this.historyService  = historyService;
    }

    @GetMapping("/")
    public String index(Model model) {
        // Recent 5 favorites for home widget
        java.util.List<io.github.claudetoolkit.ui.favorites.Favorite> allFavs = favoriteService.findAll();
        model.addAttribute("recentFavorites",
                allFavs.size() > 5 ? allFavs.subList(0, 5) : allFavs);
        model.addAttribute("favCount", allFavs.size());

        // Recent 5 history entries for home widget
        java.util.List<io.github.claudetoolkit.ui.history.ReviewHistory> allHistory = historyService.findAll();
        model.addAttribute("recentHistory",
                allHistory.size() > 5 ? allHistory.subList(0, 5) : allHistory);
        model.addAttribute("historyCount", historyService.count());

        return "index";
    }
}
