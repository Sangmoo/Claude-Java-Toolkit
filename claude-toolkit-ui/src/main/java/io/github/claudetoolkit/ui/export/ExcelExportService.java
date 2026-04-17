package io.github.claudetoolkit.ui.export;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v4.3.0 — 분석 이력을 Excel(.xlsx) 워크북으로 내보내는 서비스.
 *
 * <p>3개 시트 구성:
 * <ul>
 *   <li><b>요약</b> — 총 건수, 유형별 분포, 토큰 합계 (수식 포함)</li>
 *   <li><b>이력 상세</b> — 행 단위 이력 데이터 (id, type, title, username, status, tokens, createdAt)</li>
 *   <li><b>유형별 통계</b> — type 별 카운트 + 비율</li>
 * </ul>
 */
@Service
public class ExcelExportService {

    private static final Logger log = LoggerFactory.getLogger(ExcelExportService.class);
    private static final DateTimeFormatter DT_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 이력 목록을 Excel 워크북 바이트 배열로 변환.
     */
    public byte[] toExcel(List<ReviewHistory> histories) throws IOException {
        if (histories == null) histories = java.util.Collections.emptyList();

        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            CellStyle headerStyle = buildHeaderStyle(wb);
            CellStyle dateStyle   = buildDateStyle(wb);
            CellStyle numberStyle = buildNumberStyle(wb);
            CellStyle wrapStyle   = buildWrapStyle(wb);

            // ── Sheet 1: 이력 상세 ────────────────────────────────────────
            Sheet detailSheet = wb.createSheet("이력 상세");
            writeDetailSheet(detailSheet, histories, headerStyle, dateStyle, numberStyle, wrapStyle);

            // ── Sheet 2: 유형별 통계 ──────────────────────────────────────
            Sheet typeSheet = wb.createSheet("유형별 통계");
            writeTypeBreakdown(typeSheet, histories, headerStyle, numberStyle);

            // ── Sheet 3: 요약 (수식 포함) ─────────────────────────────────
            Sheet summarySheet = wb.createSheet("요약");
            writeSummarySheet(summarySheet, histories.size(), headerStyle, numberStyle);

            // 시트 순서를 요약 → 상세 → 유형별 로 재배치
            wb.setSheetOrder("요약", 0);
            wb.setActiveSheet(0);

            wb.write(out);
            byte[] bytes = out.toByteArray();
            log.info("Excel 내보내기 성공: rows={}, bytes={}", histories.size(), bytes.length);
            return bytes;
        }
    }

    // ── 시트 작성 메서드들 ─────────────────────────────────────────────────

    private void writeDetailSheet(Sheet sheet, List<ReviewHistory> histories,
                                  CellStyle headerStyle, CellStyle dateStyle,
                                  CellStyle numberStyle, CellStyle wrapStyle) {
        String[] headers = {
                "ID", "유형", "유형 라벨", "제목", "작성자",
                "리뷰 상태", "리뷰어", "리뷰 시각",
                "입력 토큰", "출력 토큰", "총 토큰",
                "생성 시각", "출력 미리보기"
        };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        for (ReviewHistory h : histories) {
            Row row = sheet.createRow(rowIdx++);
            int c = 0;
            row.createCell(c++).setCellValue(h.getId());
            row.createCell(c++).setCellValue(nullSafe(h.getType()));
            row.createCell(c++).setCellValue(nullSafe(h.getTypeLabel()));
            row.createCell(c++).setCellValue(nullSafe(h.getTitle()));
            row.createCell(c++).setCellValue(nullSafe(h.getUsername()));
            row.createCell(c++).setCellValue(nullSafe(h.getReviewStatus()));
            row.createCell(c++).setCellValue(nullSafe(h.getReviewedBy()));

            Cell reviewedAtCell = row.createCell(c++);
            if (h.getReviewedAt() != null) {
                reviewedAtCell.setCellValue(h.getReviewedAt().format(DT_FMT));
                reviewedAtCell.setCellStyle(dateStyle);
            }

            Cell inTokCell  = row.createCell(c++);
            Cell outTokCell = row.createCell(c++);
            Cell totTokCell = row.createCell(c++);
            if (h.getInputTokens() != null) {
                inTokCell.setCellValue(h.getInputTokens());
                inTokCell.setCellStyle(numberStyle);
            }
            if (h.getOutputTokens() != null) {
                outTokCell.setCellValue(h.getOutputTokens());
                outTokCell.setCellStyle(numberStyle);
            }
            totTokCell.setCellValue(h.getTotalTokens());
            totTokCell.setCellStyle(numberStyle);

            Cell createdCell = row.createCell(c++);
            if (h.getCreatedAt() != null) {
                createdCell.setCellValue(h.getCreatedAt().format(DT_FMT));
                createdCell.setCellStyle(dateStyle);
            }

            Cell previewCell = row.createCell(c);
            previewCell.setCellValue(truncate(h.getOutputPreview(), 500));
            previewCell.setCellStyle(wrapStyle);
        }

        // 자동 열 너비 (출력 미리보기 제외 — 너무 넓어지지 않도록)
        for (int i = 0; i < headers.length - 1; i++) sheet.autoSizeColumn(i);
        sheet.setColumnWidth(headers.length - 1, 80 * 256); // 미리보기는 80자

        // 헤더 행 고정
        sheet.createFreezePane(0, 1);
    }

    private void writeTypeBreakdown(Sheet sheet, List<ReviewHistory> histories,
                                    CellStyle headerStyle, CellStyle numberStyle) {
        Map<String, long[]> stats = new HashMap<String, long[]>(); // type → [count, totalTokens]
        for (ReviewHistory h : histories) {
            String key = h.getType() != null ? h.getType() : "UNKNOWN";
            long[] s = stats.get(key);
            if (s == null) {
                s = new long[]{ 0L, 0L };
                stats.put(key, s);
            }
            s[0]++;
            s[1] += h.getTotalTokens();
        }

        String[] headers = { "유형", "건수", "총 토큰", "비율 (%)" };
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
            cell.setCellStyle(headerStyle);
        }

        int rowIdx = 1;
        long total = histories.size();
        for (Map.Entry<String, long[]> e : stats.entrySet()) {
            Row row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(e.getKey());

            Cell countCell = row.createCell(1);
            countCell.setCellValue(e.getValue()[0]);
            countCell.setCellStyle(numberStyle);

            Cell tokCell = row.createCell(2);
            tokCell.setCellValue(e.getValue()[1]);
            tokCell.setCellStyle(numberStyle);

            Cell pctCell = row.createCell(3);
            // 비율을 셀 수식으로 — "이력 상세!A행수" 와 별개로 자체 계산
            pctCell.setCellFormula("ROUND(B" + rowIdx + "/" + Math.max(1, total) + "*100, 2)");
            pctCell.setCellStyle(numberStyle);
        }

        // 합계 행
        if (rowIdx > 1) {
            Row totalRow = sheet.createRow(rowIdx);
            Cell labelCell = totalRow.createCell(0);
            labelCell.setCellValue("합계");
            labelCell.setCellStyle(headerStyle);

            Cell sumCount = totalRow.createCell(1);
            sumCount.setCellFormula("SUM(B2:B" + rowIdx + ")");
            sumCount.setCellStyle(headerStyle);

            Cell sumTok = totalRow.createCell(2);
            sumTok.setCellFormula("SUM(C2:C" + rowIdx + ")");
            sumTok.setCellStyle(headerStyle);

            Cell sumPct = totalRow.createCell(3);
            sumPct.setCellFormula("SUM(D2:D" + rowIdx + ")");
            sumPct.setCellStyle(headerStyle);
        }

        for (int i = 0; i < headers.length; i++) sheet.autoSizeColumn(i);
        sheet.createFreezePane(0, 1);
    }

    private void writeSummarySheet(Sheet sheet, int totalCount,
                                   CellStyle headerStyle, CellStyle numberStyle) {
        // 제목 영역 (병합)
        Row titleRow = sheet.createRow(0);
        Cell titleCell = titleRow.createCell(0);
        titleCell.setCellValue("Claude Java Toolkit — 이력 내보내기");
        Font titleFont = sheet.getWorkbook().createFont();
        titleFont.setBold(true);
        titleFont.setFontHeightInPoints((short) 14);
        CellStyle titleStyle = sheet.getWorkbook().createCellStyle();
        titleStyle.setFont(titleFont);
        titleCell.setCellStyle(titleStyle);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 3));

        Row blank1 = sheet.createRow(1);
        blank1.createCell(0).setCellValue("");

        // 메타 정보
        Row metaHeader = sheet.createRow(2);
        Cell mh1 = metaHeader.createCell(0); mh1.setCellValue("항목"); mh1.setCellStyle(headerStyle);
        Cell mh2 = metaHeader.createCell(1); mh2.setCellValue("값");   mh2.setCellStyle(headerStyle);

        int r = 3;
        addKv(sheet, r++, "내보낸 시각", LocalDateTime.now().format(DT_FMT), numberStyle);
        addKv(sheet, r++, "총 이력 수",   String.valueOf(totalCount),         numberStyle);
        addKv(sheet, r++, "출처",        "Claude Java Toolkit v4.3.0",        numberStyle);
        addKv(sheet, r++, "참고",        "수식 합계는 '유형별 통계' 시트에서 확인", numberStyle);

        sheet.autoSizeColumn(0);
        sheet.autoSizeColumn(1);
    }

    private void addKv(Sheet sheet, int rowIdx, String key, String value, CellStyle style) {
        Row row = sheet.createRow(rowIdx);
        row.createCell(0).setCellValue(key);
        Cell v = row.createCell(1);
        v.setCellValue(value);
        v.setCellStyle(style);
    }

    // ── 스타일 빌더들 ─────────────────────────────────────────────────────

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

    private CellStyle buildDateStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.CENTER);
        return s;
    }

    private CellStyle buildNumberStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setAlignment(HorizontalAlignment.RIGHT);
        DataFormat fmt = wb.createDataFormat();
        s.setDataFormat(fmt.getFormat("#,##0"));
        return s;
    }

    private CellStyle buildWrapStyle(Workbook wb) {
        CellStyle s = wb.createCellStyle();
        s.setWrapText(true);
        s.setVerticalAlignment(VerticalAlignment.TOP);
        return s;
    }

    private String nullSafe(String s) { return s != null ? s : ""; }

    private String truncate(String s, int max) {
        if (s == null) return "";
        if (s.length() <= max) return s;
        return s.substring(0, max) + " …";
    }
}
