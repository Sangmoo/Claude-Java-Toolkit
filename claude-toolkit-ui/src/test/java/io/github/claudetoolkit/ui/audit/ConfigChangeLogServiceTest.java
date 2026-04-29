package io.github.claudetoolkit.ui.audit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * v4.7.x — ConfigChangeLogService 의 *순수 함수* 만 단위 테스트.
 *
 * <p>SecurityContext / RequestContextHolder / Repository 의존이 있는 메서드는
 * 통합 테스트 (별도) 에서 검증. 여기선 {@code maskValue} 로직만 — 가장 보안적으로
 * 민감하고 회귀 위험이 큰 부분이라 단위 검증으로 빠른 피드백.
 */
class ConfigChangeLogServiceTest {

    @Test
    @DisplayName("maskValue — null 입력은 null 그대로 (NPE 없음)")
    void mask_null() {
        assertNull(ConfigChangeLogService.maskValue(null));
    }

    @Test
    @DisplayName("maskValue — 빈 문자열은 그대로 빈 문자열")
    void mask_empty() {
        assertEquals("", ConfigChangeLogService.maskValue(""));
    }

    @Test
    @DisplayName("maskValue — 8자 미만은 전체 마스킹 (****)")
    void mask_short() {
        assertEquals("****", ConfigChangeLogService.maskValue("abc"));
        assertEquals("****", ConfigChangeLogService.maskValue("1234567"));  // 7자
    }

    @Test
    @DisplayName("maskValue — 8자 이상은 앞 4 + ... + 뒤 2 + 길이 정보")
    void mask_long() {
        // 일반 토큰 (URL 아님)
        String key  = "sk-ant-api03-abcdefghijklmnopqrstuvwxyz012345678";
        String mask = ConfigChangeLogService.maskValue(key);
        assertTrue(mask.startsWith("sk-a"), "앞 4자 보존, 실제: " + mask);
        assertTrue(mask.contains("..."), "마스킹 ellipsis 포함");
        assertTrue(mask.endsWith("(총 " + key.length() + "자)"), "길이 정보 끝에 부착, 실제: " + mask);
        // 원본 비밀 노출 금지 — 가운데 토큰 부분이 그대로 들어가 있으면 안 됨
        assertFalse(mask.contains("abcdefghijklmnopqrstuvwxyz"), "원본 시크릿 가운데 부분이 노출되면 마스킹 실패");
    }

    @Test
    @DisplayName("maskValue — URL 패턴은 last segment 마스킹 (host/path 노출)")
    void mask_url() {
        String hook = "https://hooks.slack.com/services/T01ABC/B02XYZ/abcdef0123456789";
        String mask = ConfigChangeLogService.maskValue(hook);
        // host + 앞 path 는 노출되어야 (디버깅 + identification 목적)
        assertTrue(mask.startsWith("https://hooks.slack.com/services/"),
                "host + path prefix 노출, 실제: " + mask);
        // 마지막 token 은 가려져야
        assertTrue(mask.endsWith("****"), "마지막 segment 마스킹, 실제: " + mask);
        assertFalse(mask.contains("abcdef0123456789"), "원본 토큰이 그대로 노출되면 마스킹 실패");
    }

    @Test
    @DisplayName("maskValue — http 로 시작하지 않으면 URL 분기 진입 안 함")
    void mask_nonHttpStringNotTreatedAsUrl() {
        String s = "ftp://server/secret-file";  // http 아님
        String mask = ConfigChangeLogService.maskValue(s);
        // 일반 토큰 분기 → "ftp:" + ... + 뒤 2자
        assertTrue(mask.startsWith("ftp:"));
        assertTrue(mask.contains("..."));
    }

    @Test
    @DisplayName("recordIfChanged — 값이 같으면 noop (변경 없음으로 간주, 저장 안 함)")
    void recordIfChanged_noopWhenEqual() {
        // null/빈/같은 값 시나리오 — repository 가 null 이어도 통과해야 한다
        // (Service 가 비교 후 즉시 return 해야 NPE 발생 X)
        ConfigChangeLogService svc = new ConfigChangeLogService(null);
        // 같은 값 → repo 호출 없이 빠져야 (null repo 인데 NPE 안 남 = 정상 동작)
        assertDoesNotThrow(() -> svc.recordIfChanged(
                "k", "Label", "SETTINGS", "v1", "v1", false));
        assertDoesNotThrow(() -> svc.recordIfChanged(
                "k", "Label", "SETTINGS", null, null, false));
        assertDoesNotThrow(() -> svc.recordIfChanged(
                "k", "Label", "SETTINGS", "", null, false));
        assertDoesNotThrow(() -> svc.recordIfChanged(
                "k", "Label", "SETTINGS", null, "", false));
    }
}
