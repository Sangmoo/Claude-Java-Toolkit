package io.github.claudetoolkit.ui.dashboard;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * v4.3.0 — 사용자별 홈 대시보드 위젯 레이아웃 영속화.
 *
 * <p>한 사용자가 여러 위젯 인스턴스를 가질 수 있으며, 각 인스턴스는
 * 위치(x, y), 크기(w, h), 가시성(visible), 위젯별 옵션(configJson) 을 가진다.
 *
 * <p>react-grid-layout 의 Layout[] 형식과 1:1 매핑된다.
 */
@Entity
@Table(name = "user_dashboard_layout",
       uniqueConstraints = @UniqueConstraint(columnNames = {"username", "widgetKey"}))
public class UserDashboardLayout {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    /** 위젯 식별자 — 프론트의 위젯 카탈로그 키 (예: "tools-grid", "team-activity", "roi-chart") */
    @Column(nullable = false, length = 50)
    private String widgetKey;

    @Column(nullable = false)
    private int x = 0;

    @Column(nullable = false)
    private int y = 0;

    /** 그리드 컬럼 너비 (12 컬럼 기준) */
    @Column(nullable = false)
    private int w = 6;

    /** 그리드 행 높이 */
    @Column(nullable = false)
    private int h = 4;

    @Column(nullable = false)
    private boolean visible = true;

    /** 위젯별 추가 설정 (JSON 문자열) — 향후 위젯별 옵션 확장에 대비 */
    @Column(columnDefinition = "TEXT")
    private String configJson;

    public UserDashboardLayout() {}

    public UserDashboardLayout(String username, String widgetKey, int x, int y, int w, int h, boolean visible) {
        this.username  = username;
        this.widgetKey = widgetKey;
        this.x = x; this.y = y; this.w = w; this.h = h;
        this.visible = visible;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getWidgetKey() { return widgetKey; }
    public int getX() { return x; }
    public int getY() { return y; }
    public int getW() { return w; }
    public int getH() { return h; }
    public boolean isVisible() { return visible; }
    public String getConfigJson() { return configJson; }

    public void setUsername(String s) { this.username = s; }
    public void setWidgetKey(String s) { this.widgetKey = s; }
    public void setX(int v) { this.x = v; }
    public void setY(int v) { this.y = v; }
    public void setW(int v) { this.w = v; }
    public void setH(int v) { this.h = v; }
    public void setVisible(boolean v) { this.visible = v; }
    public void setConfigJson(String s) { this.configJson = s; }
}
