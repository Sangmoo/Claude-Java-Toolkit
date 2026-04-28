package io.github.claudetoolkit.ui.compliance;

import javax.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * v4.6.x Stage 4 — 생성된 컴플라이언스 리포트의 영속 저장 엔티티.
 *
 * <p>이전엔 in-memory LRU 100건만 캐시 → 서버 재시작 시 사라짐. 외부감사·내부감사
 * 대응 시 *과거 리포트를 다시 보고* 비교해야 하는 일이 잦아 영구 저장 필요.
 *
 * <p>주의: markdown 본문이 길어질 수 있으므로 (한 리포트 약 4-8KB) {@code @Lob}
 * 사용. 누적 리포트 100\~1000개는 H2 에서 부담 없음.
 */
@Entity
@Table(name = "compliance_report", indexes = {
        @Index(name = "idx_compliance_created", columnList = "createdAt"),
        @Index(name = "idx_compliance_type",    columnList = "type")
})
public class ComplianceReportRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long id;

    /** 리포트 타입 키 — fss / privacy / network-act / external-audit */
    @Column(nullable = false, length = 30)
    private String type;

    /** 한국어 라벨 — 검색 시 노출용 */
    @Column(nullable = false, length = 100)
    private String typeLabel;

    /** 감사 시작일 */
    @Column(nullable = false)
    private LocalDate auditFrom;

    /** 감사 종료일 */
    @Column(nullable = false)
    private LocalDate auditTo;

    /** 리포트 생성 시각 (감사 기간과 별도) */
    @Column(nullable = false)
    private LocalDateTime createdAt;

    /** 생성자 username */
    @Column(length = 50)
    private String generatedBy;

    /** 전체 markdown 본문 (4-8KB 일반적, AI 요약 포함시 ~10KB) */
    @Lob
    @Column(nullable = false, columnDefinition = "TEXT")
    private String markdown;

    /** AI 경영진 요약이 포함되었는지 — 비용/생성 시간 분류용 */
    @Column(nullable = false)
    private boolean hasExecutiveSummary;

    /** 핵심 지표 4개 (감사 한 줄 보기용 — 본문 안 까봐도 알 수 있게) */
    @Column private long totalAnalysisInPeriod;
    @Column private long highSeverityCount;
    @Column private long loginFailures;
    @Column private long maskingActivities;

    protected ComplianceReportRecord() {}

    public ComplianceReportRecord(String type, String typeLabel,
                                  LocalDate auditFrom, LocalDate auditTo,
                                  String generatedBy, String markdown,
                                  boolean hasExecutiveSummary,
                                  long totalAnalysisInPeriod,
                                  long highSeverityCount,
                                  long loginFailures,
                                  long maskingActivities) {
        this.type                  = type;
        this.typeLabel             = typeLabel;
        this.auditFrom             = auditFrom;
        this.auditTo               = auditTo;
        this.createdAt             = LocalDateTime.now();
        this.generatedBy           = generatedBy;
        this.markdown              = markdown;
        this.hasExecutiveSummary   = hasExecutiveSummary;
        this.totalAnalysisInPeriod = totalAnalysisInPeriod;
        this.highSeverityCount     = highSeverityCount;
        this.loginFailures         = loginFailures;
        this.maskingActivities     = maskingActivities;
    }

    public long          getId()                  { return id; }
    public String        getType()                { return type; }
    public String        getTypeLabel()           { return typeLabel; }
    public LocalDate     getAuditFrom()           { return auditFrom; }
    public LocalDate     getAuditTo()             { return auditTo; }
    public LocalDateTime getCreatedAt()           { return createdAt; }
    public String        getGeneratedBy()         { return generatedBy; }
    public String        getMarkdown()            { return markdown; }
    public boolean       isHasExecutiveSummary()  { return hasExecutiveSummary; }
    public long          getTotalAnalysisInPeriod() { return totalAnalysisInPeriod; }
    public long          getHighSeverityCount()   { return highSeverityCount; }
    public long          getLoginFailures()       { return loginFailures; }
    public long          getMaskingActivities()   { return maskingActivities; }

    /** yyyy-MM-dd HH:mm 형식 */
    public String getFormattedCreatedAt() {
        return createdAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
}
