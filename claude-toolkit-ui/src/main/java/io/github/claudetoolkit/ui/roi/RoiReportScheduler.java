package io.github.claudetoolkit.ui.roi;

import io.github.claudetoolkit.ui.email.EmailService;
import io.github.claudetoolkit.ui.history.ReviewHistory;
import io.github.claudetoolkit.ui.history.ReviewHistoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * ROI 리포트 자동 발송 스케줄러.
 *
 * <ul>
 *   <li>매월 1일 09:00 — 직전 달 ROI 요약을 이메일로 발송</li>
 *   <li>매 정시 — 이번 달 누적 비용이 monthlyBudgetUsd 초과 시 경고 이메일 발송</li>
 * </ul>
 */
@Component
public class RoiReportScheduler {

    private static final Logger log = LoggerFactory.getLogger(RoiReportScheduler.class);

    private final ReviewHistoryService historyService;
    private final EmailService         emailService;

    /** 마지막 예산 초과 알림 날짜 (중복 발송 방지) */
    private LocalDate lastBudgetAlertDate = null;

    public RoiReportScheduler(ReviewHistoryService historyService,
                               EmailService emailService) {
        this.historyService = historyService;
        this.emailService   = emailService;
    }

    // ── 매월 1일 09:00 — 직전 달 ROI 리포트 발송 ─────────────────────────────

    @Scheduled(cron = "0 0 9 1 * ?")
    public void sendMonthlyReport() {
        RoiSettings settings = RoiSettings.load();
        String alertEmail = settings.getBudgetAlertEmail();
        if (alertEmail == null || alertEmail.trim().isEmpty()) return;

        YearMonth lastMonth = YearMonth.now().minusMonths(1);
        String ym = lastMonth.format(DateTimeFormatter.ofPattern("yyyy년 MM월"));

        // 직전 달 이력 필터링
        List<ReviewHistory> all = historyService.findAll();
        List<ReviewHistory> filtered = new ArrayList<ReviewHistory>();
        for (ReviewHistory h : all) {
            if (YearMonth.from(h.getCreatedAt()).equals(lastMonth)) {
                filtered.add(h);
            }
        }

        RoiCalculator calc = new RoiCalculator(settings);
        RoiCalculator.SummaryData summary = calc.calcSummary(filtered, 1);

        String subject = "[AI Toolkit] " + ym + " ROI 리포트";
        String body    = buildMonthlyBody(ym, summary, filtered, calc, settings);

        try {
            emailService.sendJobResult(alertEmail, subject, body);
        } catch (Exception e) {
            log.error("[RoiScheduler] 월간 리포트 발송 실패: " + e.getMessage());
        }
    }

    // ── 매 정시 — 예산 초과 알림 ─────────────────────────────────────────────

    @Scheduled(cron = "0 0 * * * ?")
    public void checkBudgetAlert() {
        RoiSettings settings = RoiSettings.load();
        double budget = settings.getMonthlyBudgetUsd();
        String alertEmail = settings.getBudgetAlertEmail();

        if (budget <= 0 || alertEmail == null || alertEmail.trim().isEmpty()) return;

        // 오늘 이미 발송했으면 스킵
        LocalDate today = LocalDate.now();
        if (today.equals(lastBudgetAlertDate)) return;

        // 이번 달 누적 AI 비용 계산
        YearMonth thisMonth = YearMonth.now();
        List<ReviewHistory> all = historyService.findAll();
        long inputTok = 0L, outputTok = 0L;
        for (ReviewHistory h : all) {
            if (!YearMonth.from(h.getCreatedAt()).equals(thisMonth)) continue;
            if (h.getInputTokens()  != null) inputTok  += h.getInputTokens();
            if (h.getOutputTokens() != null) outputTok += h.getOutputTokens();
        }
        double costUsd = (inputTok  / 1_000_000.0 * settings.getInputCostPerMillion())
                       + (outputTok / 1_000_000.0 * settings.getOutputCostPerMillion());

        if (costUsd < budget) return;

        lastBudgetAlertDate = today;
        String subject = "[AI Toolkit] ⚠️ 이번 달 API 비용 예산 초과 경고";
        String body = "<h3>Claude API 월 예산 초과 알림</h3>"
                    + "<p>이번 달 누적 API 비용: <strong>$" + String.format("%.2f", costUsd) + "</strong></p>"
                    + "<p>설정된 월 예산 한도: $" + String.format("%.2f", budget) + "</p>"
                    + "<p>초과 금액: $" + String.format("%.2f", costUsd - budget) + "</p>"
                    + "<p>자세한 내용은 <a href=\"http://localhost:8027/roi-report\">ROI 리포트</a>를 확인하세요.</p>";
        try {
            emailService.sendJobResult(alertEmail, subject, body);
        } catch (Exception e) {
            log.error("[RoiScheduler] 예산 초과 알림 발송 실패: " + e.getMessage());
        }
    }

    // ── 이메일 본문 구성 ──────────────────────────────────────────────────────

    private String buildMonthlyBody(String ym, RoiCalculator.SummaryData s,
                                    List<ReviewHistory> filtered,
                                    RoiCalculator calc, RoiSettings settings) {
        List<RoiCalculator.FeatureRoiData> features = calc.calcByFeature(filtered, 1);
        // Top 3 by savedCostWon
        features.sort(new java.util.Comparator<RoiCalculator.FeatureRoiData>() {
            public int compare(RoiCalculator.FeatureRoiData a, RoiCalculator.FeatureRoiData b) {
                return Double.compare(b.savedCostWon, a.savedCostWon);
            }
        });

        StringBuilder sb = new StringBuilder();
        sb.append("<h2>").append(ym).append(" AI Toolkit ROI 리포트</h2>");
        sb.append("<table border='1' cellpadding='8' cellspacing='0' style='border-collapse:collapse;'>");
        sb.append("<tr><th>항목</th><th>수치</th></tr>");
        sb.append("<tr><td>총 분석 건수</td><td>").append(s.totalCount).append("건</td></tr>");
        sb.append("<tr><td>절감 시간</td><td>")
          .append(String.format("%.0f", s.totalSavedMin / 60.0)).append("시간 (")
          .append(s.totalSavedMin).append("분)</td></tr>");
        sb.append("<tr><td>절감 비용</td><td>").append(formatWon(s.totalSavedWon)).append("</td></tr>");
        sb.append("<tr><td>AI 사용 비용</td><td>").append(formatWon(s.totalAiWon)).append("</td></tr>");
        sb.append("<tr><td><strong>순 이익</strong></td><td><strong>").append(formatWon(s.totalNetWon)).append("</strong></td></tr>");
        sb.append("<tr><td><strong>ROI</strong></td><td><strong>").append(String.format("%.1f%%", s.overallRoi)).append("</strong></td></tr>");
        sb.append("</table>");

        if (!features.isEmpty()) {
            sb.append("<h3>기능별 절감비용 Top 3</h3>");
            sb.append("<table border='1' cellpadding='8' cellspacing='0' style='border-collapse:collapse;'>");
            sb.append("<tr><th>기능</th><th>건수</th><th>절감비용</th></tr>");
            int limit = Math.min(3, features.size());
            for (int i = 0; i < limit; i++) {
                RoiCalculator.FeatureRoiData f = features.get(i);
                sb.append("<tr><td>").append(f.type).append("</td><td>")
                  .append(f.usageCount).append("건</td><td>")
                  .append(formatWon(f.savedCostWon)).append("</td></tr>");
            }
            sb.append("</table>");
        }
        sb.append("<p><a href=\"http://localhost:8027/roi-report\">ROI 리포트 상세 보기</a></p>");
        return sb.toString();
    }

    private String formatWon(double won) {
        return String.format("%,.0f원", won);
    }
}
