package io.github.claudetoolkit.ui.compliance;

import io.github.claudetoolkit.ui.compliance.template.ExternalAuditTemplate;
import io.github.claudetoolkit.ui.compliance.template.FssRegulationTemplate;
import io.github.claudetoolkit.ui.compliance.template.NetworkActTemplate;
import io.github.claudetoolkit.ui.compliance.template.PrivacyActTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * v4.6.x — 컴플라이언스 리포트 생성 서비스 (Stage 1: FSS 만 구현).
 *
 * <p>흐름: ComplianceDataAggregator 가 review_history / audit_log 에서
 * 통계를 모으고, 타입별 템플릿 빌더가 markdown 으로 변환. 결과는 in-memory
 * LRU 에 100건만 보관 (다운로드용).
 *
 * <p>Stage 2 에서 PRIVACY / NETWORK_ACT / EXTERNAL_AUDIT 템플릿 추가 예정.
 */
@Service
public class ComplianceReportService {

    private static final Logger log = LoggerFactory.getLogger(ComplianceReportService.class);

    private static final DateTimeFormatter STAMP_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** 생성된 리포트 LRU 보관소 — UUID → markdown */
    private final Map<String, GeneratedReport> reports =
            java.util.Collections.synchronizedMap(new LinkedHashMap<String, GeneratedReport>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, GeneratedReport> eldest) {
                    return size() > 100;
                }
            });

    /** 영구 저장 상한 — 초과 시 가장 오래된 것부터 자동 prune */
    private static final long PERSISTED_MAX = 500;

    private final ComplianceDataAggregator aggregator;
    private final ExecutiveSummaryGenerator execSummary;
    private final ComplianceExcelExporter excelExporter;
    private final ComplianceReportRepository reportRepo;

    public ComplianceReportService(ComplianceDataAggregator aggregator,
                                   ExecutiveSummaryGenerator execSummary,
                                   ComplianceExcelExporter excelExporter,
                                   ComplianceReportRepository reportRepo) {
        this.aggregator    = aggregator;
        this.execSummary   = execSummary;
        this.excelExporter = excelExporter;
        this.reportRepo    = reportRepo;
    }

    /**
     * 리포트 생성 → 메모리에 저장 + 응답에 markdown 본문 포함.
     */
    public GeneratedReport generate(ComplianceReportType type,
                                    LocalDate from,
                                    LocalDate to,
                                    String generatedBy) {
        return generate(type, from, to, generatedBy, false);
    }

    /**
     * 리포트 생성 + (옵션) Claude 기반 경영진 요약 prepend.
     * @param withExecutiveSummary true 면 Claude API 호출 → 결과는 markdown 상단 삽입.
     *                              실패 시 기본 markdown 만 반환 (서비스 안정성 우선).
     */
    public GeneratedReport generate(ComplianceReportType type,
                                    LocalDate from,
                                    LocalDate to,
                                    String generatedBy,
                                    boolean withExecutiveSummary) {
        if (type == null || !type.isEnabled()) {
            throw new IllegalArgumentException(
                    "현재 사용 가능한 리포트 타입이 아닙니다: " +
                            (type != null ? type.getKey() : "null"));
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from / to 날짜는 필수입니다");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from 이 to 보다 늦을 수 없습니다");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) > 366) {
            throw new IllegalArgumentException("기간은 1년 이내로 지정해주세요");
        }

        log.info("[Compliance] 리포트 생성 시작 type={} from={} to={} by={}",
                type.getKey(), from, to, generatedBy);

        ComplianceData data = aggregator.aggregate(from, to);
        data.type        = type;
        data.generatedAt = LocalDateTime.now().format(STAMP_FMT);
        data.generatedBy = generatedBy != null ? generatedBy : "(unknown)";

        String markdown;
        switch (type) {
            case FSS:
                markdown = FssRegulationTemplate.build(data);
                break;
            case PRIVACY:
                markdown = PrivacyActTemplate.build(data);
                break;
            case NETWORK_ACT:
                markdown = NetworkActTemplate.build(data);
                break;
            case EXTERNAL_AUDIT:
                markdown = ExternalAuditTemplate.build(data);
                break;
            default:
                throw new IllegalStateException(
                        "알 수 없는 리포트 타입: " + type.getKey());
        }

        // 옵션: 경영진 요약 prepend
        String execSummaryText = null;
        if (withExecutiveSummary) {
            execSummaryText = execSummary.generate(data);
            if (execSummaryText != null && !execSummaryText.isEmpty()) {
                StringBuilder withSummary = new StringBuilder(markdown.length() + execSummaryText.length() + 200);
                // 첫 번째 # 헤딩 라인 위치를 찾아서 그 다음 줄에 요약 삽입
                int firstHeadEnd = markdown.indexOf("\n");
                if (firstHeadEnd > 0) {
                    withSummary.append(markdown, 0, firstHeadEnd + 1);
                    withSummary.append("\n## 🤖 경영진 요약 (AI 생성)\n\n");
                    withSummary.append(execSummaryText).append("\n\n");
                    withSummary.append("> 위 요약은 Claude AI 가 자동 생성. 본문 데이터를 우선시하세요.\n\n");
                    withSummary.append(markdown, firstHeadEnd + 1, markdown.length());
                    markdown = withSummary.toString();
                }
            }
        }

        // v4.6.x Stage 4 — 영구 저장 (이력 페이지에서 다시 조회 가능)
        ComplianceReportRecord saved;
        try {
            ComplianceReportRecord rec = new ComplianceReportRecord(
                    type.getKey(),
                    type.getLabel(),
                    from,
                    to,
                    data.generatedBy,
                    markdown,
                    execSummaryText != null && !execSummaryText.isEmpty(),
                    data.totalAnalysisInPeriod,
                    data.security != null ? data.security.highSeverityCount : 0,
                    data.auth     != null ? data.auth.loginFailures        : 0,
                    data.masking  != null ? (data.masking.maskingAnalyses + data.masking.inputMaskingUses) : 0
            );
            saved = reportRepo.save(rec);
            pruneOverLimit();
        } catch (Exception persistErr) {
            // 영속화 실패해도 사용자 응답은 정상 → 메모리 LRU 만 사용
            log.warn("[Compliance] 영구 저장 실패 — 메모리 LRU 만 사용: {}", persistErr.getMessage());
            saved = null;
        }

        GeneratedReport gr = new GeneratedReport();
        // 영속화 성공시 DB id 사용 (이력 페이지에서 동일 id 로 다시 조회), 실패시 UUID
        gr.id          = saved != null ? String.valueOf(saved.getId()) : UUID.randomUUID().toString();
        gr.type        = type;
        gr.from        = from;
        gr.to          = to;
        gr.generatedAt = data.generatedAt;
        gr.generatedBy = data.generatedBy;
        gr.markdown    = markdown;
        gr.aggregatedData = data;  // Excel exporter 가 raw 데이터 사용
        gr.executiveSummary = execSummaryText;
        gr.suggestedFilename = String.format("compliance-%s-%s_%s.md",
                type.getKey(), from, to);

        reports.put(gr.id, gr);
        log.info("[Compliance] 리포트 생성 완료 id={} length={}자 execSummary={} persisted={}",
                gr.id, markdown.length(), execSummaryText != null, saved != null);
        return gr;
    }

    /** 영속 상한 초과 시 가장 오래된 레코드부터 삭제 */
    private void pruneOverLimit() {
        try {
            while (reportRepo.count() > PERSISTED_MAX) {
                ComplianceReportRecord oldest = reportRepo.findTopByOrderByCreatedAtAsc();
                if (oldest == null) break;
                reportRepo.delete(oldest);
            }
        } catch (Exception ignored) { /* prune 실패해도 메인 흐름엔 영향 X */ }
    }

    /**
     * v4.6.x Stage 4 — 저장된 리포트 이력 목록 (최신순).
     * markdown 본문은 제외하고 메타데이터만 (목록 화면 빠르게).
     */
    public List<ComplianceReportRecord> listHistory(int limit) {
        int safeLimit = Math.max(1, Math.min(200, limit));
        return reportRepo.findRecent(org.springframework.data.domain.PageRequest.of(0, safeLimit));
    }

    /**
     * v4.6.x Stage 4 — 영구 저장된 리포트를 DB id 로 다시 로드 (markdown 포함).
     * GeneratedReport DTO 형태로 변환해서 반환 — 프론트가 동일 객체 형태로 다룸.
     */
    public GeneratedReport loadFromHistory(long recordId) {
        ComplianceReportRecord rec = reportRepo.findById(recordId).orElse(null);
        if (rec == null) return null;
        ComplianceReportType type = ComplianceReportType.fromKey(rec.getType());
        if (type == null) return null;
        GeneratedReport gr = new GeneratedReport();
        gr.id              = String.valueOf(rec.getId());
        gr.type            = type;
        gr.from            = rec.getAuditFrom();
        gr.to              = rec.getAuditTo();
        gr.generatedAt     = rec.getFormattedCreatedAt() + ":00";
        gr.generatedBy     = rec.getGeneratedBy();
        gr.markdown        = rec.getMarkdown();
        gr.executiveSummary = null; // markdown 안에 이미 포함됨
        gr.aggregatedData  = null;  // Excel 재생성은 불가 — 새로 생성 필요
        gr.suggestedFilename = String.format("compliance-%s-%s_%s.md",
                rec.getType(), rec.getAuditFrom(), rec.getAuditTo());
        // LRU 에도 등록 (다운로드 엔드포인트가 캐시 우선 조회)
        reports.put(gr.id, gr);
        return gr;
    }

    /** v4.6.x Stage 4 — 이력 항목 삭제 */
    public boolean deleteHistory(long recordId) {
        if (!reportRepo.existsById(recordId)) return false;
        reportRepo.deleteById(recordId);
        // LRU 에서도 제거
        reports.remove(String.valueOf(recordId));
        return true;
    }

    /** Excel(.xlsx) 바이트 변환 — 리포트 ID 로 조회된 경우만 가능. */
    public byte[] toExcel(String reportId) throws java.io.IOException {
        GeneratedReport gr = find(reportId);
        if (gr == null || gr.aggregatedData == null) {
            throw new IllegalStateException("리포트가 만료되었거나 데이터가 없습니다 — 다시 생성하세요");
        }
        return excelExporter.toExcel(gr, gr.aggregatedData);
    }

    /**
     * id 로 조회 — 다운로드 시점에 markdown 을 가져오는 데 사용.
     * LRU 캐시 우선, 미스 시 DB 에서 로드 (이력 페이지에서 과거 리포트 다운로드 가능하게).
     */
    public GeneratedReport find(String id) {
        GeneratedReport cached = reports.get(id);
        if (cached != null) return cached;
        // 캐시 미스 → DB 조회 시도 (id 가 long parsable 일 때만)
        try {
            long recId = Long.parseLong(id);
            return loadFromHistory(recId);
        } catch (NumberFormatException ignore) {
            return null;
        }
    }

    /** 생성된 리포트 메타데이터. */
    public static class GeneratedReport {
        public String id;
        public ComplianceReportType type;
        public LocalDate from;
        public LocalDate to;
        public String generatedAt;
        public String generatedBy;
        public String markdown;
        public String suggestedFilename;
        /** v4.6.x Stage 3: AI 경영진 요약 (옵션) */
        public String executiveSummary;
        /** v4.6.x Stage 3: Excel 내보내기용 raw 데이터 (in-memory) */
        public ComplianceData aggregatedData;
    }
}
