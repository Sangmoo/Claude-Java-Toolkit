package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.ui.favorites.Favorite;
import io.github.claudetoolkit.ui.favorites.FavoriteService;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
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

    /**
     * Star a history entry → save as favorite.
     * Called via POST from any result page or the history page.
     */
    @PostMapping("/star")
    @ResponseBody
    public Map<String, Object> star(
            @RequestParam("historyId")                          long   historyId,
            @RequestParam(value = "title",   defaultValue = "") String title,
            @RequestParam(value = "tag",     defaultValue = "") String tag,
            Authentication auth) {

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        ReviewHistory h = historyService.findById(historyId);
        if (h == null) {
            result.put("success", false);
            result.put("error",   "이력을 찾을 수 없습니다.");
            return result;
        }
        // v4.2.7: 로그인 사용자를 owner 로 저장하고 historyId 를 보존해야
        // /api/v1/favorites 조회(username 필터)와 프론트의 별표 반영 상태 체크가 동작한다.
        String owner = (auth != null) ? auth.getName() : null;
        Favorite saved = favoriteService.save(
                h.getType(),
                title.isEmpty() ? h.getTitle() : title,
                tag,
                h.getInputContent(),
                h.getOutputContent(),
                owner,
                Long.valueOf(historyId));
        result.put("success",    true);
        result.put("favoriteId", saved.getId());
        result.put("historyId",  historyId);
        return result;
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
            @RequestParam(value = "redirect",defaultValue = "/favorites") String redirect,
            Authentication auth) {

        String owner = (auth != null) ? auth.getName() : null;
        favoriteService.save(type, title, tag, inputContent, outputContent, owner, null);
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

    /**
     * Delete a single favorite.
     *
     * <p>v4.2.7 — 소유자 체크 + JSON 응답. 본인 소유가 아니면 HTTP 403.
     * 기존엔 `@PathVariable id` 만 받고 redirect 를 돌려 누구든 남의 즐겨찾기를
     * 삭제할 수 있는 구멍이었다.
     */
    @PostMapping("/{id}/delete")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> delete(@PathVariable long id,
                                                       Authentication auth) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        String owner = (auth != null) ? auth.getName() : null;
        if (owner == null) {
            resp.put("success", false);
            resp.put("error",   "로그인이 필요합니다.");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(resp);
        }
        if (!favoriteService.isOwnedBy(id, owner)) {
            resp.put("success", false);
            resp.put("error",   "본인의 즐겨찾기만 삭제할 수 있습니다.");
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(resp);
        }
        boolean ok = favoriteService.deleteById(id);
        resp.put("success", ok);
        if (!ok) resp.put("error", "즐겨찾기를 찾을 수 없습니다.");
        return ResponseEntity.ok(resp);
    }

    /** Clear all favorites */
    @PostMapping("/clear")
    public String clear() {
        favoriteService.clear();
        return "redirect:/favorites";
    }
}
