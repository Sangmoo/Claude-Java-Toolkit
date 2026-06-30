package io.github.claudetoolkit.ui.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class McpServerHealthIndicatorTest {

    @Mock
    private McpServerLauncher launcher;

    private McpServerHealthIndicator indicator;

    @BeforeEach
    void setUp() {
        indicator = new McpServerHealthIndicator(launcher);
    }

    @Test
    @DisplayName("auto-start=false → UP (disabled)")
    void autoStartDisabled() {
        when(launcher.isAutoStart()).thenReturn(false);

        Health h = indicator.health();

        assertEquals(Status.UP, h.getStatus());
        assertEquals("disabled", h.getDetails().get("status"));
    }

    @Test
    @DisplayName("auto-start=true + 프로세스 실행 중 → UP (running) + pid + port")
    void runningWithPid() {
        when(launcher.isAutoStart()).thenReturn(true);
        when(launcher.isRunning()).thenReturn(true);
        when(launcher.getPid()).thenReturn(12345L);
        when(launcher.getPort()).thenReturn(8028);

        Health h = indicator.health();

        assertEquals(Status.UP, h.getStatus());
        assertEquals("running",  h.getDetails().get("status"));
        assertEquals(12345L,     h.getDetails().get("pid"));
        assertEquals(8028,       h.getDetails().get("port"));
    }

    @Test
    @DisplayName("auto-start=true + 프로세스 실행 중 + pid=null → UP (pid 키 없음)")
    void runningNoPid() {
        when(launcher.isAutoStart()).thenReturn(true);
        when(launcher.isRunning()).thenReturn(true);
        when(launcher.getPid()).thenReturn(null);
        when(launcher.getPort()).thenReturn(8028);

        Health h = indicator.health();

        assertEquals(Status.UP, h.getStatus());
        assertFalse(h.getDetails().containsKey("pid"), "pid null 이면 detail 에서 제외");
    }

    @Test
    @DisplayName("auto-start=true + 프로세스 중단 → DOWN")
    void notRunning() {
        when(launcher.isAutoStart()).thenReturn(true);
        when(launcher.isRunning()).thenReturn(false);

        Health h = indicator.health();

        assertEquals(Status.DOWN, h.getStatus());
        assertEquals("not running", h.getDetails().get("status"));
        assertNotNull(h.getDetails().get("action"), "조치 안내 detail 포함");
    }
}
