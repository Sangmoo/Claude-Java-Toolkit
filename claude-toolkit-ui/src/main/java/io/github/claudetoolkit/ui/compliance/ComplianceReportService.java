package io.github.claudetoolkit.ui.compliance;

import io.github.claudetoolkit.ui.compliance.template.FssRegulationTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
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

    private final ComplianceDataAggregator aggregator;

    public ComplianceReportService(ComplianceDataAggregator aggregator) {
        this.aggregator = aggregator;
    }

    /**
     * 리포트 생성 → 메모리에 저장 + 응답에 markdown 본문 포함.
     */
    public GeneratedReport generate(ComplianceReportType type,
                                    LocalDate from,
                                    LocalDate to,
                                    String generatedBy) {
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
            // Stage 2 — 나머지 3종 템플릿 추가 예정
            case PRIVACY:
            case NETWORK_ACT:
            case EXTERNAL_AUDIT:
            default:
                throw new IllegalStateException(
                        "이 타입은 아직 구현되지 않았습니다 (Stage 2 예정): " + type.getKey());
        }

        GeneratedReport gr = new GeneratedReport();
        gr.id          = UUID.randomUUID().toString();
        gr.type        = type;
        gr.from        = from;
        gr.to          = to;
        gr.generatedAt = data.generatedAt;
        gr.generatedBy = data.generatedBy;
        gr.markdown    = markdown;
        gr.suggestedFilename = String.format("compliance-%s-%s_%s.md",
                type.getKey(), from, to);

        reports.put(gr.id, gr);
        log.info("[Compliance] 리포트 생성 완료 id={} length={}자", gr.id, markdown.length());
        return gr;
    }

    /** id 로 조회 — 다운로드 시점에 markdown 을 가져오는 데 사용 */
    public GeneratedReport find(String id) {
        return reports.get(id);
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
    }
}
