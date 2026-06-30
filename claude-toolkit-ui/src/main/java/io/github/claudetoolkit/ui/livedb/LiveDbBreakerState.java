package io.github.claudetoolkit.ui.livedb;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * 회로차단기 오픈 상태를 DB 테이블에 영속 — 멀티 인스턴스 배포에서 인스턴스간 상태 공유.
 *
 * <p>단일 인스턴스 환경에서는 in-memory 맵과 동일하게 동작하지만,
 * 수평 확장 시 다른 인스턴스의 차단 결정이 공유 DB 를 통해 전파된다.
 *
 * <p>행이 없으면 CLOSED, 행이 있으면 OPEN (openedAt 기준으로 만료 여부 판단).
 */
@Entity
@Table(name = "livedb_breaker_state")
public class LiveDbBreakerState {

    @Id
    @Column(name = "profile_id")
    private Long profileId;

    /** 회로 차단 시작 시각 (epoch ms) */
    @Column(name = "opened_at", nullable = false)
    private long openedAt;

    protected LiveDbBreakerState() {}

    public LiveDbBreakerState(Long profileId, long openedAt) {
        this.profileId = profileId;
        this.openedAt  = openedAt;
    }

    public Long getProfileId() { return profileId; }
    public long getOpenedAt()  { return openedAt; }
    public void setOpenedAt(long openedAt) { this.openedAt = openedAt; }
}
