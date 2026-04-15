package io.github.claudetoolkit.ui.favorites;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

/**
 * Favorites service backed by H2 file database (JPA).
 *
 * Persists up to maxFavorites entries. Oldest entries are automatically
 * pruned when the limit is exceeded. Data survives server restarts.
 */
@Service
@Transactional
public class FavoriteService {

    /**
     * v4.2.7 — 즐겨찾기 상한을 설정(`toolkit.favorites.max`) 으로 외부화.
     * 기본 500 (이전 하드코딩값 200 대비 상향).
     */
    @Value("${toolkit.favorites.max:500}")
    private int maxFavorites;

    private final FavoriteRepository repository;

    public FavoriteService(FavoriteRepository repository) {
        this.repository = repository;
    }

    public Favorite save(String type, String title, String tag,
                         String inputContent, String outputContent) {
        return save(type, title, tag, inputContent, outputContent, null, null);
    }

    /**
     * v4.2.7 — 소유자(username) 와 원본 이력 ID(historyId) 를 명시적으로 저장.
     * 기존 save() 는 호환용 오버로드.
     *
     * <p>중복 방지: username + historyId 가 지정된 경우 기존 Favorite 이 있으면 재사용하고
     * 새로 생성하지 않는다 (동일 별 아이콘 재클릭시 중복 로우 생성 방지).
     */
    public Favorite save(String type, String title, String tag,
                         String inputContent, String outputContent,
                         String username, Long historyId) {
        // v4.2.7: (username, historyId) 중복 체크
        if (username != null && historyId != null) {
            Favorite existing = repository.findByUsernameAndHistoryId(username, historyId).orElse(null);
            if (existing != null) return existing;
        }
        Favorite f = new Favorite(type, autoTitle(type, title != null ? title : inputContent),
                                   tag != null ? tag : "", inputContent, outputContent);
        f.setUsername(username);
        f.setHistoryId(historyId);
        Favorite saved = repository.save(f);
        long cnt = repository.count();
        if (cnt > maxFavorites) {
            Favorite oldest = repository.findTopByOrderByCreatedAtAsc();
            if (oldest != null) repository.delete(oldest);
        }
        return saved;
    }

    /**
     * v4.2.7 — 본인 소유 여부 확인용. /favorites/{id}/delete 엔드포인트에서 호출되며
     * username 이 null 이거나 Favorite 자체가 없으면 false.
     */
    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public boolean isOwnedBy(long favoriteId, String username) {
        if (username == null || username.isEmpty()) return false;
        Favorite f = repository.findById(favoriteId).orElse(null);
        return f != null && username.equals(f.getUsername());
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Favorite> findAll() {
        return repository.findRecentEntries(PageRequest.of(0, maxFavorites));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Favorite> findByType(String type) {
        return repository.findByType(type, PageRequest.of(0, maxFavorites));
    }

    @org.springframework.transaction.annotation.Transactional(readOnly = true)
    public List<Favorite> findByTag(String tag) {
        if (tag == null || tag.trim().isEmpty()) return findAll();
        return repository.findByTagContaining(tag.trim(), PageRequest.of(0, maxFavorites));
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
