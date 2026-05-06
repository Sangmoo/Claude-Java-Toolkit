package io.github.claudetoolkit.ui.dbprofile;

/**
 * v4.7.x — #G3 보강 B1: DbProfile 의 *내용 변경* 이벤트.
 *
 * <p>{@link DbProfileService#update}, {@link DbProfileService#deleteById},
 * {@link DbProfileService#toggleLiveAnalysis} 호출 시 발행. Live DB 모듈의
 * {@code LiveDbContextService} / {@code IndexSimulatorService} 가 listener 로
 * 받아 캐시를 invalidate — 변경 후 stale connection / password 사용 사고 방지.
 *
 * <p><b>설계 의도</b>: livedb 모듈이 dbprofile 모듈을 *직접 의존하지 않게*
 * (반대 방향으로만 의존) 하기 위해 이벤트 사용. dbprofile 모듈은 이 event 만
 * 발행하면 되고, listener 가 누구냐는 알 필요 없음.
 */
public class DbProfileChangedEvent {

    public enum Type { UPDATED, DELETED, LIVE_ANALYSIS_TOGGLED }

    private final Long profileId;
    private final Type type;

    public DbProfileChangedEvent(Long profileId, Type type) {
        this.profileId = profileId;
        this.type      = type;
    }

    public Long getProfileId() { return profileId; }
    public Type getType()      { return type; }
}
