package io.github.claudetoolkit.ui.livedb;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * H2 인메모리 DB 를 사용하는 순수 JPA 단위 테스트.
 * DDL auto-create 로 livedb_breaker_state 테이블이 자동 생성됨.
 */
@DataJpaTest
class LiveDbBreakerStateRepositoryTest {

    @Autowired
    private LiveDbBreakerStateRepository repo;

    @Test
    void saveFindDelete() {
        long now = System.currentTimeMillis();
        repo.save(new LiveDbBreakerState(1L, now));

        Optional<LiveDbBreakerState> found = repo.findById(1L);
        assertThat(found).isPresent();
        assertThat(found.get().getOpenedAt()).isEqualTo(now);

        repo.deleteById(1L);
        assertThat(repo.findById(1L)).isEmpty();
    }

    @Test
    void findAll_returnsAllSavedRows() {
        repo.save(new LiveDbBreakerState(10L, 1000L));
        repo.save(new LiveDbBreakerState(20L, 2000L));

        assertThat(repo.findAll()).hasSize(2);
    }

    @Test
    void deleteById_nonExistent_doesNotThrow() {
        repo.deleteById(99L);   // 존재하지 않는 id — 예외 없이 무시
    }
}
