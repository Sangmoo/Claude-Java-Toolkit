package io.github.claudetoolkit.ui.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase D — 새 하네스 feature key가 어드민 권한 화면에 노출되는지 검증.
 *
 * <p>새 페이지/하위 기능을 추가했을 때 {@link AdminPermissionController#FEATURES}에
 * 등록을 빠뜨리면 어드민이 그 기능을 토글할 수 없게 되므로(fail-open),
 * 회귀 방지용 테스트로 핵심 키들의 존재를 보증합니다.
 */
class AdminPermissionControllerFeatureRegistrationTest {

    @Test
    @DisplayName("loganalyzer + loganalyzer-harness가 모두 FEATURES에 등록되어 있음")
    void logAnalyzerHarnessFeatureRegistered() {
        AdminPermissionController controller = new AdminPermissionController(null, null);
        ResponseEntity<List<Map<String, Object>>> resp = controller.listFeatures();
        assertNotNull(resp.getBody());

        boolean foundBase    = false;
        boolean foundHarness = false;
        for (Map<String, Object> category : resp.getBody()) {
            @SuppressWarnings("unchecked")
            List<Map<String, String>> items = (List<Map<String, String>>) category.get("items");
            for (Map<String, String> item : items) {
                if ("loganalyzer".equals(item.get("key")))         foundBase    = true;
                if ("loganalyzer-harness".equals(item.get("key"))) foundHarness = true;
            }
        }
        assertTrue(foundBase,    "loganalyzer 기본 키 누락 — 회귀!");
        assertTrue(foundHarness, "loganalyzer-harness 신규 키 누락 — Phase D 권한 게이팅 깨짐");
    }

    @Test
    @DisplayName("Phase C — sql-optimization-harness 키가 '분석' 카테고리에 등록")
    void sqlOptimizationHarnessFeatureRegistered() {
        AdminPermissionController controller = new AdminPermissionController(null, null);
        ResponseEntity<List<Map<String, Object>>> resp = controller.listFeatures();

        for (Map<String, Object> category : resp.getBody()) {
            if (!"분석".equals(category.get("category"))) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, String>> items = (List<Map<String, String>>) category.get("items");
            for (Map<String, String> item : items) {
                if ("sql-optimization-harness".equals(item.get("key"))) {
                    assertTrue(item.get("label").contains("SQL"),
                            "라벨에 'SQL' 포함 필요: " + item.get("label"));
                    return;
                }
            }
        }
        fail("sql-optimization-harness가 '분석' 카테고리에 없음 — Phase C 권한 게이팅 깨짐");
    }

    @Test
    @DisplayName("Phase B — sp-migration-harness 키가 '분석' 카테고리에 등록")
    void spMigrationHarnessFeatureRegistered() {
        AdminPermissionController controller = new AdminPermissionController(null, null);
        ResponseEntity<List<Map<String, Object>>> resp = controller.listFeatures();

        for (Map<String, Object> category : resp.getBody()) {
            if (!"분석".equals(category.get("category"))) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, String>> items = (List<Map<String, String>>) category.get("items");
            for (Map<String, String> item : items) {
                if ("sp-migration-harness".equals(item.get("key"))) {
                    assertTrue(item.get("label").contains("SP"),
                            "라벨에 'SP' 포함 필요: " + item.get("label"));
                    return;
                }
            }
        }
        fail("sp-migration-harness가 '분석' 카테고리에 없음 — Phase B 권한 게이팅 깨짐");
    }

    @Test
    @DisplayName("loganalyzer-harness가 '도구' 카테고리에 위치")
    void logAnalyzerHarnessInCorrectCategory() {
        AdminPermissionController controller = new AdminPermissionController(null, null);
        ResponseEntity<List<Map<String, Object>>> resp = controller.listFeatures();

        for (Map<String, Object> category : resp.getBody()) {
            if (!"도구".equals(category.get("category"))) continue;
            @SuppressWarnings("unchecked")
            List<Map<String, String>> items = (List<Map<String, String>>) category.get("items");
            for (Map<String, String> item : items) {
                if ("loganalyzer-harness".equals(item.get("key"))) {
                    assertTrue(item.get("label").contains("로그 분석기"),
                            "라벨에 '로그 분석기' 포함 필요: " + item.get("label"));
                    return;
                }
            }
        }
        fail("loganalyzer-harness가 '도구' 카테고리에 없음");
    }
}
