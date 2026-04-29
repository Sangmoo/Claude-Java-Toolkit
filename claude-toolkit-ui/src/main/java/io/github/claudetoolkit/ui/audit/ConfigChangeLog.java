package io.github.claudetoolkit.ui.audit;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * v4.7.x — Settings 변경 감사 로그 엔티티.
 *
 * <p>외부감사·정보보호 점검 시 *"누가 언제 어떤 항목을 바꿨는가?"* 를
 * 추적할 수 있도록 *변경 이전/이후 값* 을 기록. 민감 값(API Key, password,
 * Webhook URL 안의 token 등) 은 자동 마스킹.
 *
 * <p>review_history / audit_log 와 동일한 H2 / 외부 DB 에 저장. 보존정책은
 * 기본 무제한 (외부감사 요구로 다년간 보관 필요할 수 있어 자동 삭제 안 함).
 */
@Entity
@Table(name = "config_change_log", indexes = {
        @Index(name = "idx_config_change_at",   columnList = "changedAt"),
        @Index(name = "idx_config_change_key",  columnList = "configKey"),
        @Index(name = "idx_config_change_user", columnList = "changedBy")
})
public class ConfigChangeLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** 변경된 설정 키 (예: "claude.api.key", "db.url", "project.scanPath", "permission.user.kim") */
    @Column(nullable = false, length = 100)
    private String configKey;

    /** 사람이 읽기 쉬운 라벨 (예: "Claude API Key") */
    @Column(nullable = false, length = 150)
    private String configLabel;

    /**
     * 변경 카테고리 — UI 필터 + 외부감사 보고서 그룹핑용.
     * 가능한 값: SETTINGS / PERMISSION / DB_PROFILE / SECURITY / ROI
     */
    @Column(nullable = false, length = 30)
    private String category;

    /** 변경 이전 값 — 민감 값은 마스킹 처리됨 (널 가능: 신규 추가) */
    @Column(columnDefinition = "TEXT")
    private String oldValue;

    /** 변경 이후 값 — 민감 값은 마스킹 처리됨 (널 가능: 삭제) */
    @Column(columnDefinition = "TEXT")
    private String newValue;

    /** 민감 값으로 분류되어 마스킹된 항목인지 (UI 에서 🔒 아이콘 표시용) */
    @Column(nullable = false)
    private boolean sensitive;

    /** 변경한 사용자명 (Spring Security Authentication 에서 추출) */
    @Column(nullable = false, length = 50)
    private String changedBy;

    /** 변경 시각 */
    @Column(nullable = false)
    private LocalDateTime changedAt;

    /** 클라이언트 IP — Settings 화면에서 변경시 audit 보강용 (널 가능) */
    @Column(length = 60)
    private String ipAddress;

    protected ConfigChangeLog() {}

    public ConfigChangeLog(String configKey, String configLabel, String category,
                           String oldValue, String newValue, boolean sensitive,
                           String changedBy, String ipAddress) {
        this.configKey   = configKey;
        this.configLabel = configLabel;
        this.category    = category;
        this.oldValue    = oldValue;
        this.newValue    = newValue;
        this.sensitive   = sensitive;
        this.changedBy   = changedBy != null ? changedBy : "(system)";
        this.ipAddress   = ipAddress;
        this.changedAt   = LocalDateTime.now();
    }

    public long          getId()            { return id; }
    public String        getConfigKey()     { return configKey; }
    public String        getConfigLabel()   { return configLabel; }
    public String        getCategory()      { return category; }
    public String        getOldValue()      { return oldValue; }
    public String        getNewValue()      { return newValue; }
    public boolean       isSensitive()      { return sensitive; }
    public String        getChangedBy()     { return changedBy; }
    public LocalDateTime getChangedAt()     { return changedAt; }
    public String        getIpAddress()     { return ipAddress; }

    /** UI 표시용 — yyyy-MM-dd HH:mm:ss */
    public String getFormattedChangedAt() {
        return changedAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * 변경 종류 추정 — UI 의 색상/아이콘 표시용.
     * CREATE: oldValue == null && newValue != null
     * DELETE: oldValue != null && newValue == null
     * UPDATE: 둘 다 존재
     */
    public String getOperation() {
        boolean hasOld = oldValue != null && !oldValue.isEmpty();
        boolean hasNew = newValue != null && !newValue.isEmpty();
        if (!hasOld && hasNew)  return "CREATE";
        if (hasOld && !hasNew)  return "DELETE";
        return "UPDATE";
    }
}
