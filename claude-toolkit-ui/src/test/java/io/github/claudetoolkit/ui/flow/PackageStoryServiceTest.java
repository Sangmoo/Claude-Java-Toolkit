package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * v4.5 — PackageStoryService 단위 테스트.
 *
 * <p>커버:
 * <ul>
 *   <li>비어있는 패키지 → Claude 호출 없음, "빈 패키지" 메시지</li>
 *   <li>Claude 호출 성공 → 마크다운 캐시</li>
 *   <li>같은 키 재요청 → fromCache=true, Claude 재호출 없음</li>
 *   <li>fresh=true → 캐시 무시하고 재호출</li>
 *   <li>Claude 예외 → markdown 에 에러 메시지 + result.error 세팅</li>
 * </ul>
 */
class PackageStoryServiceTest {

    private PackageAnalysisService packageService;
    private ClaudeClient           claudeClient;
    private PackageStoryService    storyService;

    @BeforeEach
    void setUp() {
        packageService = mock(PackageAnalysisService.class);
        claudeClient   = mock(ClaudeClient.class);
        storyService   = new PackageStoryService(packageService, claudeClient);
    }

    @Test
    @DisplayName("빈 패키지 → Claude 호출하지 않고 안내 메시지 반환")
    void emptyPackage_skipsClaudeCall() throws Exception {
        when(packageService.getDetail(anyString(), anyInt()))
                .thenReturn(emptyDetail("com.empty"));

        PackageStoryService.StoryResult r = storyService.generate("com.empty", 5, false);

        assertNotNull(r.markdown);
        assertTrue(r.markdown.contains("빈 패키지"), "빈 패키지 안내가 포함되어야 함: " + r.markdown);
        verifyNoInteractions(claudeClient);
    }

    @Test
    @DisplayName("Claude 호출 성공 → markdown 캐시")
    void claudeCallSucceeds_cachesResult() throws Exception {
        when(packageService.getDetail(anyString(), anyInt()))
                .thenReturn(populatedDetail("com.foo"));
        when(claudeClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn("## 🎯 이 패키지는 무엇을 하나요?\n샘플 응답");

        PackageStoryService.StoryResult r = storyService.generate("com.foo", 5, false);

        assertNotNull(r.markdown);
        assertTrue(r.markdown.contains("샘플 응답"));
        assertNull(r.error);
        assertFalse(r.fromCache);
        assertEquals(1, storyService.cacheSize());
    }

    @Test
    @DisplayName("같은 키 재요청 → fromCache=true, Claude 재호출 없음")
    void cacheHit_doesNotCallClaude() throws Exception {
        when(packageService.getDetail(anyString(), anyInt()))
                .thenReturn(populatedDetail("com.foo"));
        when(claudeClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn("첫 호출 결과");

        storyService.generate("com.foo", 5, false);          // 캐시 등록
        PackageStoryService.StoryResult r2 = storyService.generate("com.foo", 5, false);

        assertTrue(r2.fromCache);
        assertEquals("첫 호출 결과", r2.markdown);
        verify(claudeClient, times(1)).chat(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("fresh=true → 캐시 무시하고 재호출")
    void freshTrue_bypassesCache() throws Exception {
        when(packageService.getDetail(anyString(), anyInt()))
                .thenReturn(populatedDetail("com.foo"));
        when(claudeClient.chat(anyString(), anyString(), anyInt()))
                .thenReturn("결과 1", "결과 2");

        storyService.generate("com.foo", 5, false);               // 캐시
        PackageStoryService.StoryResult r2 =
                storyService.generate("com.foo", 5, true);        // 강제 재호출

        assertFalse(r2.fromCache);
        assertEquals("결과 2", r2.markdown);
        verify(claudeClient, times(2)).chat(anyString(), anyString(), anyInt());
    }

    @Test
    @DisplayName("Claude 예외 → markdown 에 안내 + error 세팅 (catch 처리)")
    void claudeThrows_returnsErrorMarkdown() throws Exception {
        when(packageService.getDetail(anyString(), anyInt()))
                .thenReturn(populatedDetail("com.foo"));
        when(claudeClient.chat(anyString(), anyString(), anyInt()))
                .thenAnswer((Answer<String>) (InvocationOnMock invocation) -> {
                    throw new RuntimeException("network down");
                });

        PackageStoryService.StoryResult r = storyService.generate("com.foo", 5, false);

        assertNotNull(r.markdown);
        assertTrue(r.markdown.contains("스토리 생성 실패") || r.markdown.contains("network down"),
                "에러 안내가 포함되어야 함: " + r.markdown);
        assertEquals("network down", r.error);
        assertEquals(0, storyService.cacheSize(), "에러 결과는 캐시되지 않아야 함");
    }

    // ── 헬퍼 ───────────────────────────────────────────────────────

    private static PackageAnalysisService.PackageDetail emptyDetail(String name) {
        PackageAnalysisService.PackageDetail d = new PackageAnalysisService.PackageDetail();
        d.packageName = name;
        d.classTotal  = 0;
        return d;
    }

    private static PackageAnalysisService.PackageDetail populatedDetail(String name) {
        PackageAnalysisService.PackageDetail d = new PackageAnalysisService.PackageDetail();
        d.packageName     = name;
        d.classTotal      = 5;
        d.controllerCount = 1;
        d.serviceCount    = 2;
        d.daoCount        = 1;
        d.modelCount      = 1;
        d.mybatisCount    = 3;
        d.tableCount      = 2;
        d.endpointCount   = 4;
        d.classes              = new ArrayList<>();
        d.endpoints            = new ArrayList<>();
        d.mybatisStatements    = new ArrayList<>();
        d.tables               = new ArrayList<>();
        d.tables.add("USERS");
        d.tables.add("ORDERS");
        d.externalDependencies = new ArrayList<>();
        return d;
    }
}
