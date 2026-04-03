package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.favorites.Favorite;
import io.github.claudetoolkit.ui.favorites.FavoriteService;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Web controller for Favorites (/favorites).
 */
@Controller
@RequestMapping("/favorites")
public class FavoriteController {

    private final FavoriteService     favoriteService;
    private final ReviewHistoryService historyService;

    public FavoriteController(FavoriteService favoriteService,
                              ReviewHistoryService historyService) {
        this.favoriteService = favoriteService;
        this.historyService  = historyService;
    }

    /** List all favorites */
    @GetMapping
    public String list(Model model) {
        model.addAttribute("favorites", favoriteService.findAll());
        model.addAttribute("count",     favoriteService.count());
        return "favorites/index";
    }

    /**
     * Star a history entry → save as favorite.
     * Called via POST from any result page or the history page.
     */
    @PostMapping("/star")
    public String star(
            @RequestParam("historyId")                          long   historyId,
            @RequestParam(value = "title",   defaultValue = "") String title,
            @RequestParam(value = "tag",     defaultValue = "") String tag,
            @RequestParam(value = "redirect",defaultValue = "/history") String redirect) {

        ReviewHistory h = historyService.findById(historyId);
        if (h != null) {
            favoriteService.save(h.getType(),
                    title.isEmpty() ? h.getTitle() : title,
                    tag,
                    h.getInputContent(),
                    h.getOutputContent());
        }
        return "redirect:" + redirect;
    }

    /**
     * Save directly from any result form (not via history).
     */
    @PostMapping("/save")
    public String save(
            @RequestParam("type")                               String type,
            @RequestParam(value = "title",   defaultValue = "") String title,
            @RequestParam(value = "tag",     defaultValue = "") String tag,
            @RequestParam("inputContent")                       String inputContent,
            @RequestParam("outputContent")                      String outputContent,
            @RequestParam(value = "redirect",defaultValue = "/favorites") String redirect) {

        favoriteService.save(type, title, tag, inputContent, outputContent);
        return "redirect:" + redirect;
    }

    /** Detail view (AJAX JSON) */
    @GetMapping("/{id}/detail")
    @ResponseBody
    public Map<String, String> detail(@PathVariable long id) {
        Map<String, String> map = new LinkedHashMap<String, String>();
        Favorite f = favoriteService.findById(id);
        if (f == null) { map.put("error", "Not found"); return map; }
        map.put("type",   f.getTypeLabel());
        map.put("title",  f.getTitle());
        map.put("tag",    f.getTag() != null ? f.getTag() : "");
        map.put("date",   f.getFormattedDate());
        map.put("input",  f.getInputContent());
        map.put("output", f.getOutputContent());
        return map;
    }

    /** Delete a single favorite */
    @PostMapping("/{id}/delete")
    public String delete(@PathVariable long id,
                         @RequestParam(value = "redirect", defaultValue = "/favorites") String redirect) {
        favoriteService.deleteById(id);
        return "redirect:" + redirect;
    }

    /** Clear all favorites */
    @PostMapping("/clear")
    public String clear() {
        favoriteService.clear();
        return "redirect:/favorites";
    }
}
