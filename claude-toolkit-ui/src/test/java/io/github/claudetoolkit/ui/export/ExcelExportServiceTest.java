package io.github.claudetoolkit.ui.export;

import io.github.claudetoolkit.ui.history.ReviewHistory;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.4.0 — ExcelExportService 단위 테스트.
 *
 * <p>3-시트 .xlsx 워크북 구조 + 헤더/데이터/수식을 검증.
 */
class ExcelExportServiceTest {

    private ExcelExportService service;

    @BeforeEach
    void setUp() {
        service = new ExcelExportService();
    }

    @Test
    @DisplayName("빈 이력 — 3 시트 (요약/이력 상세/유형별 통계) 모두 생성")
    void emptyHistoryStillProducesAllSheets() throws Exception {
        byte[] bytes = service.toExcel(Collections.emptyList());
        assertNotNull(bytes);
        assertTrue(bytes.length > 0);

        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(3, wb.getNumberOfSheets());
            assertNotNull(wb.getSheet("요약"));
            assertNotNull(wb.getSheet("이력 상세"));
            assertNotNull(wb.getSheet("유형별 통계"));
        }
    }

    @Test
    @DisplayName("요약 시트가 첫 번째 시트로 정렬")
    void summarySheetIsFirst() throws Exception {
        byte[] bytes = service.toExcel(Collections.emptyList());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals("요약", wb.getSheetAt(0).getSheetName());
            assertEquals(0, wb.getActiveSheetIndex());
        }
    }

    @Test
    @DisplayName("이력 상세 시트 — 헤더 13개 컬럼")
    void detailSheetHeaders() throws Exception {
        byte[] bytes = service.toExcel(Collections.emptyList());
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet detail = wb.getSheet("이력 상세");
            assertNotNull(detail.getRow(0));
            assertEquals("ID",         detail.getRow(0).getCell(0).getStringCellValue());
            assertEquals("유형",       detail.getRow(0).getCell(1).getStringCellValue());
            assertEquals("총 토큰",    detail.getRow(0).getCell(10).getStringCellValue());
            assertEquals("출력 미리보기", detail.getRow(0).getCell(12).getStringCellValue());
        }
    }

    @Test
    @DisplayName("이력 데이터 — 행 수가 입력 이력 수와 일치 (헤더 + N 행)")
    void detailRowCountMatchesInput() throws Exception {
        List<ReviewHistory> histories = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            histories.add(makeHistory("CODE_REVIEW", "title-" + i, 100L * (i + 1), 50L));
        }
        byte[] bytes = service.toExcel(histories);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet detail = wb.getSheet("이력 상세");
            // 헤더(0) + 5 데이터 = lastRowNum 5
            assertEquals(5, detail.getLastRowNum());
            assertEquals("title-0", detail.getRow(1).getCell(3).getStringCellValue());
        }
    }

    @Test
    @DisplayName("유형별 통계 — 동일 type 그룹화 + 합계 수식")
    void typeBreakdownGroupsAndSums() throws Exception {
        List<ReviewHistory> histories = Arrays.asList(
                makeHistory("CODE_REVIEW", "a", 100L, 50L),
                makeHistory("CODE_REVIEW", "b", 200L, 100L),
                makeHistory("SQL_REVIEW",  "c", 300L, 150L)
        );
        byte[] bytes = service.toExcel(histories);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            Sheet types = wb.getSheet("유형별 통계");
            // 헤더(0) + 2 type 행 + 합계 행 = lastRowNum 3
            assertEquals(3, types.getLastRowNum());
            // 합계 행에 SUM 수식 존재
            String formula = types.getRow(3).getCell(1).getCellFormula();
            assertTrue(formula.startsWith("SUM(B"), "건수 합계 SUM 수식: " + formula);
        }
    }

    @Test
    @DisplayName("null 입력 — 빈 리스트로 안전 처리 (NPE 없음)")
    void nullInputSafe() throws Exception {
        byte[] bytes = service.toExcel(null);
        try (Workbook wb = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            assertEquals(3, wb.getNumberOfSheets());
        }
    }

    private ReviewHistory makeHistory(String type, String title, Long inTok, Long outTok) {
        ReviewHistory h = new ReviewHistory(type, title, "in", "out", null, inTok, outTok);
        try {
            Field id = ReviewHistory.class.getDeclaredField("id");
            id.setAccessible(true);
            id.set(h, 1L);
            Field createdAt = ReviewHistory.class.getDeclaredField("createdAt");
            createdAt.setAccessible(true);
            createdAt.set(h, LocalDateTime.now());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return h;
    }
}
