package io.github.claudetoolkit.ui.smoke;

import io.github.claudetoolkit.ui.favorites.Favorite;
import io.github.claudetoolkit.ui.favorites.FavoriteRepository;
import io.github.claudetoolkit.ui.favorites.FavoriteService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * v4.2.7 — Phase 1.5: `/favorites/star` 호출이 (username, historyId) 조합에 대해
 * 중복 Favorite 을 만들지 않는지 검증.
 *
 * <p>동일 유저가 같은 이력을 두 번 별표해도 Favorite 로우는 1개만 존재해야 한다.
 * 반복 star 요청이 DB 로우를 오염시키지 않도록 `FavoriteService.save` 안에
 * findByUsernameAndHistoryId 기반 중복 체크가 들어가 있음.
 */
@SpringBootTest
@ActiveProfiles("test")
class FavoriteDedupSmokeTest {

    @Autowired
    private FavoriteService favoriteService;

    @Autowired
    private FavoriteRepository favoriteRepository;

    @Test
    @DisplayName("1.5 — 같은 user + historyId 로 save() 를 두 번 호출해도 Favorite 은 1개만 존재")
    void save_is_idempotent_per_user_and_history() {
        // 사전 정리: 테스트 격리
        favoriteRepository.deleteAll();

        final String user = "smoke-user";
        final long   historyId = 42L;

        Favorite first = favoriteService.save(
                "SQL_REVIEW", "테스트 제목", "",
                "SELECT 1 FROM DUAL", "리뷰 결과",
                user, historyId);
        assertNotNull(first, "첫 저장이 Favorite 반환");

        Favorite second = favoriteService.save(
                "SQL_REVIEW", "두 번째 호출 (무시되어야)", "",
                "SELECT 1 FROM DUAL", "다른 결과",
                user, historyId);

        assertEquals(first.getId(), second.getId(),
                "같은 (user, historyId) 조합은 기존 Favorite 을 재사용해야 함");
        assertEquals(1L,
                favoriteRepository.findByUsernameAndHistoryId(user, historyId)
                        .map(f -> 1L).orElse(0L),
                "DB 에 Favorite 로우가 정확히 1개만 있어야 함");

        // 다른 사용자는 같은 historyId 로 새 Favorite 을 만들 수 있어야 함
        Favorite other = favoriteService.save(
                "SQL_REVIEW", "다른 사용자", "",
                "SELECT 1 FROM DUAL", "결과",
                "another-user", historyId);
        assertNotNull(other);
        // 서로 다른 ID
        org.junit.jupiter.api.Assertions.assertNotEquals(first.getId(), other.getId(),
                "다른 사용자는 별도 Favorite 을 가져야 함");
    }
}
