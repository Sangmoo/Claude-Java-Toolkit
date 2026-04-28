package io.github.claudetoolkit.ui.compliance;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Map;

/**
 * v4.6.x — 컴플라이언스 리포트 Excel(.xlsx) 내보내기 (Stage 3).
 *
 * <p>4 개 시트:
 * <ol>
 *   <li>요약 — 감사 메타 + 4개 영역 핵심 지표</li>
 *   <li>보안 발견 — HIGH/MED/LOW 카운트 + HIGH 사례 행 단위 목록</li>
 *   <li>인증·권한 — audit_log 기반 카운트 + 산출 비율</li>
 *   <li>분석 활동 — type 별 카운트 + 한국어 라벨</li>
 * </ol>
 *
 * <p>외부감사인이 그대로 Pivot Table 만들어 활용 가능하도록 *raw 행 데이터* 위주.
 */
@Service
public class ComplianceExcelExporter {

    private static final Logger log = LoggerFactory.getLogger(ComplianceExcelExporter.class);

    public byte[] toExcel(ComplianceReportService.GeneratedReport gr,
                          ComplianceData data) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle titleStyle  = buildTitleStyle(wb);
            CellStyle metaStyle   = buildMetaStyle(wb);

            writeSummarySheet  (wb.createSheet("요약"),       gr, data, titleStyle, headerStyle, metaStyle);
            writeSecuritySheet (wb.createSheet("보안 발견"),    data, headerStyle);
            writeAuthSheet     (wb.createSheet("인증·권한"),    data, headerStyle);
            writeActivitySheet (wb.createSheet("분석 활동"),    data, headerStyle);

            wb.setActiveSheet(0);
            wb.write(out);
            byte[] bytes = out.toByteArray();
            log.info("[Compliance] Excel 내보내기 성공 type={} size={}바이트",
                    gr.type.getKey(), bytes.length);
            return bytes;
        }
    }

    // ── 시트 1: 요약 ────────────────────────────────────────────────────────

    private void writeSummarySheet(Sheet sheet, ComplianceReportService.GeneratedReport gr,
                                    ComplianceData d, CellStyle titleStyle,
                                    CellStyle headerStyle, CellStyle metaStyle) {
        // 제목
        Row title = sheet.createRow(0);
        Cell tc = title.createCell(0);
        tc.setCellValue(gr.type.getLabel() + " — 컴플라이언스 리포트 요약");
        tc.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        int r = 2;
        // 감사 메타
        Row metaHdr = sheet.createRow(r++);
        Cell mh1 = metaHdr.createCell(0); mh1.setCellValue("감사 메타");      mh1.setCellStyle(headerStyle);
        Cell mh2 = metaHdr.createCell(1); mh2.setCellValue("");              mh2.setCellStyle(headerStyle);
        addKv(sheet, r++, "감사 시작일",      gr.from.toString(),       metaStyle);
        addKv(sheet, r++, "감사 종료일",      gr.to.toString(),         metaStyle);
        addKv(sheet, r++, "보고서 생성",     gr.generatedAt,            metaStyle);
        addKv(sheet, r++, "생성자",          gr.generatedBy,            metaStyle);
        addKv(sheet, r++, "리포트 ID",       gr.id,                     metaStyle);

        r++;
        // 핵심 지표 — 4개 영역
        Row indHdr = sheet.createRow(r++);
        Cell ih1 = indHdr.createCell(0); ih1.setCellValue("핵심 지표");        ih1.setCellStyle(headerStyle);
        Cell ih2 = indHdr.createCell(1); ih2.setCellValue("값");              ih2.setCellStyle(headerStyle);
        addKv(sheet, r++, "기간 내 분석 건수",         String.valueOf(d.totalAnalysisInPeriod), metaStyle);
        addKv(sheet, r++, "분석 수행 사용자 수",       String.valueOf(d.totalAnalysisByUserCount), metaStyle);
        addKv(sheet, r++, "보안 분석 누적",           String.valueOf(d.security.totalSecurityReviews), metaStyle);
        addKv(sheet, r++, "🔴 HIGH 등급 발견",        String.valueOf(d.security.highSeverityCount), metaStyle);
        addKv(sheet, r++, "🟡 MEDIUM 등급 발견",      String.valueOf(d.security.mediumSeverityCount), metaStyle);
        addKv(sheet, r++, "🟢 LOW 등급 발견",         String.valueOf(d.security.lowSeverityCount), metaStyle);
        addKv(sheet, r++, "audit_log 누적",          String.valueOf(d.auth.totalAuditEntries), metaStyle);
        addKv(sheet, r++, "로그인 시도",              String.valueOf(d.auth.loginAttempts), metaStyle);
        addKv(sheet, r++, "로그인 실패",              String.valueOf(d.auth.loginFailures), metaStyle);
        addKv(sheet, r++, "권한 거부 (HTTP 403)",     String.valueOf(d.auth.permissionDenials), metaStyle);
        addKv(sheet, r++, "서버 오류 (HTTP 5xx)",     String.valueOf(d.auth.apiCalls5xx), metaStyle);
        addKv(sheet, r++, "데이터 마스킹 분석",       String.valueOf(d.masking.maskingAnalyses), metaStyle);
        addKv(sheet, r++, "입력 마스킹 사용",         String.valueOf(d.masking.inputMaskingUses), metaStyle);

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.setColumnWidth(0, Math.min(sheet.getColumnWidth(0), 8000));
    }

    // ── 시트 2: 보안 발견 ───────────────────────────────────────────────────

    private void writeSecuritySheet(Sheet sheet, ComplianceData d, CellStyle headerStyle) {
        // 등급별 카운트
        Row hdr = sheet.createRow(0);
        String[] hs = { "등급", "건수" };
        for (int i = 0; i < hs.length; i++) {
            Cell c = hdr.createCell(i);
            c.setCellValue(hs[i]); c.setCellStyle(headerStyle);
        }
        addKv(sheet, 1, "HIGH",   String.valueOf(d.security.highSeverityCount),   null);
        addKv(sheet, 2, "MEDIUM", String.valueOf(d.security.mediumSeverityCount), null);
        addKv(sheet, 3, "LOW",    String.valueOf(d.security.lowSeverityCount),    null);
        addKv(sheet, 4, "전체 보안 분석", String.valueOf(d.security.totalSecurityReviews), null);

        // HIGH 사례 표 (행 5+)
        int r = 6;
        Row caseHdr = sheet.createRow(r++);
        String[] caseCols = { "ID", "유형", "유형 라벨", "제목", "사용자", "일시" };
        for (int i = 0; i < caseCols.length; i++) {
            Cell c = caseHdr.createCell(i);
            c.setCellValue(caseCols[i]); c.setCellStyle(headerStyle);
        }
        if (d.security.recentHighFindings != null) {
            for (Map<String, Object> f : d.security.recentHighFindings) {
                Row row = sheet.createRow(r++);
                row.createCell(0).setCellValue(safe(f.get("id")));
                row.createCell(1).setCellValue(safe(f.get("type")));
                row.createCell(2).setCellValue(safe(f.get("typeLabel")));
                row.createCell(3).setCellValue(safe(f.get("title")));
                row.createCell(4).setCellValue(safe(f.get("username")));
                row.createCell(5).setCellValue(safe(f.get("createdAt")));
            }
        }
        for (int i = 0; i < caseCols.length; i++) sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, 7);
    }

    // ── 시트 3: 인증·권한 ───────────────────────────────────────────────────

    private void writeAuthSheet(Sheet sheet, ComplianceData d, CellStyle headerStyle) {
        Row hdr = sheet.createRow(0);
        Cell h0 = hdr.createCell(0); h0.setCellValue("항목");      h0.setCellStyle(headerStyle);
        Cell h1 = hdr.createCell(1); h1.setCellValue("발생 횟수"); h1.setCellStyle(headerStyle);
        Cell h2 = hdr.createCell(2); h2.setCellValue("비율 / 비고"); h2.setCellStyle(headerStyle);

        long total = d.auth.totalAuditEntries;
        long att   = d.auth.loginAttempts;
        addRow(sheet, 1, "전체 API 호출 (audit_log)",  total, "");
        addRow(sheet, 2, "로그인 시도",                 att,
                total > 0 ? String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * att / total) : "");
        addRow(sheet, 3, "로그인 실패 (4xx)",           d.auth.loginFailures,
                att > 0 ? String.format(java.util.Locale.ROOT, "실패율 %.1f%%", 100.0 * d.auth.loginFailures / att) : "");
        addRow(sheet, 4, "권한 거부 (HTTP 403)",         d.auth.permissionDenials, "");
        addRow(sheet, 5, "서버 오류 (HTTP 5xx)",         d.auth.apiCalls5xx,
                total > 0 ? String.format(java.util.Locale.ROOT, "%.2f%%", 100.0 * d.auth.apiCalls5xx / total) : "");

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
        sheet.createFreezePane(0, 1);
    }

    // ── 시트 4: 분석 활동 ───────────────────────────────────────────────────

    private void writeActivitySheet(Sheet sheet, ComplianceData d, CellStyle headerStyle) {
        Row hdr = sheet.createRow(0);
        Cell h0 = hdr.createCell(0); h0.setCellValue("Type");       h0.setCellStyle(headerStyle);
        Cell h1 = hdr.createCell(1); h1.setCellValue("한국어 라벨"); h1.setCellStyle(headerStyle);
        Cell h2 = hdr.createCell(2); h2.setCellValue("건수");        h2.setCellStyle(headerStyle);

        int r = 1;
        for (Map.Entry<String, Long> e : d.activityByType.entrySet()) {
            Row row = sheet.createRow(r++);
            row.createCell(0).setCellValue(e.getKey());
            row.createCell(1).setCellValue(ReviewHistory.typeLabelOf(e.getKey()));
            row.createCell(2).setCellValue(e.getValue());
        }
        // 합계 행
        Row sumRow = sheet.createRow(r);
        Cell sumLabel = sumRow.createCell(0);
        sumLabel.setCellValue("합계"); sumLabel.setCellStyle(headerStyle);
        sumRow.createCell(1).setCellValue("");
        Cell sumCell = sumRow.createCell(2);
        if (r > 1) sumCell.setCellFormula("SUM(C2:C" + r + ")");
        else       sumCell.setCellValue(0);
        sumCell.setCellStyle(headerStyle);

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
        sheet.autoSizeColumn(2);
        sheet.createFreezePane(0, 1);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void addKv(Sheet sheet, int rowIdx, String key, String value, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        Cell v = row.createCell(1);
        v.setCellValue(value);
        if (style != null) v.setCellStyle(style);
    }

    private void addRow(Sheet sheet, int rowIdx, String label, long count, String note) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(count);
        row.createCell(2).setCellValue(note);
    }

    private static String safe(Object o) {
        return o != null ? o.toString() : "";
    }

    private CellStyle buildHeaderStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setColor(IndexedColors.WHITE.getIndex());
        s.setFont(f);
        s.setFillForegroundColor(IndexedColors.GREY_50_PERCENT.getIndex());
        s.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        s.setAlignment(HorizontalAlignment.CENTER);
        s.setBorderBottom(BorderStyle.THIN);
        return s;
    }

    private CellStyle buildTitleStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setBold(true);
        f.setFontHeightInPoints((short) 14);
        s.setFont(f);
        return s;
    }

    private CellStyle buildMetaStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        Font f = wb.createFont();
        f.setFontHeightInPoints((short) 11);
        s.setFont(f);
        return s;
    }
}
