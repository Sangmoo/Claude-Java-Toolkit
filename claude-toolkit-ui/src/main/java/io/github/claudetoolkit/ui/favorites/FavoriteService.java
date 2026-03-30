package io.github.claudetoolkit.ui.favorites;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Favorites service backed by H2 file database (JPA).
 *
 * Persists up to MAX_FAVORITES entries. Oldest entries are automatically
 * pruned when the limit is exceeded. Data survives server restarts.
 */
@Service
@Transactional
public class FavoriteService {

    private static final int MAX_FAVORITES = 200;

    private final FavoriteRepository repository;

    public FavoriteService(FavoriteRepository repository) {
        this.repository = repository;
    }

    public Favorite save(String type, String title, String tag,
                         String inputContent, String outputContent) {
        Favorite f = new Favorite(type, autoTitle(type, title != null ? title : inputContent),
                                   tag != null ? tag : "", inputContent, outputContent);
        Favorite saved = repository.save(f);
        long cnt = repository.count();
        if (cnt > MAX_FAVORITES) {
            Favorite oldest = repository.findTopByOrderByCreatedAtAsc();
            if (oldest != null) repository.delete(oldest);
        }
        return saved;
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Favorite> findAll() {
        return repository.findRecentEntries(PageRequest.of(0, MAX_FAVORITES));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Favorite> findByTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return findAll();
        return repository.findByTagContaining(tag.trim(), PageRequest.of(0, MAX_FAVORITES));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public Favorite findById(long id) {
        Favorite f = repository.findById(id).orElse(null);
        return f;
    }

    public boolean deleteById(long id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return true;
        }
        return false;
    }

    public void clear() {
        repository.deleteAll();
    }

    public int count() {
        return (int) repository.count();
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private String autoTitle(String type, String input) {
        if (input == null || input.trim().isEmpty()) return type + " 즐겨찾기";
        String trimmed = input.trim().replaceAll("\\s+", " ");
        return trimmed.length() > 60 ? trimmed.substring(0, 60) + "…" : trimmed;
    }
}
