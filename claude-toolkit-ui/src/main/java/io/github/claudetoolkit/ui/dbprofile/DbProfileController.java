package io.github.claudetoolkit.ui.dbprofile;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
@RequestMapping("/db-profiles")
public class DbProfileController {

    private final DbProfileService service;
    private final ToolkitSettings  settings;

    /** v4.7.x — #G3 보강 B4: dropdown 응답에 회로 상태 포함 (옵셔널 — Live DB 비활성 시 null) */
    @Autowired(required = false)
    private io.github.claudetoolkit.ui.livedb.LiveDbCircuitBreaker liveDbCircuitBreaker;

    /** v4.7.x — #G3 보강: 글로벌 enabled 상태를 응답에 포함 (chip UI 가 즉시 사용자에게 안내) */
    @Autowired(required = false)
    private io.github.claudetoolkit.ui.livedb.LiveDbConfig liveDbConfig;

    public DbProfileController(DbProfileService service, ToolkitSettings settings) {
        this.service  = service;
        this.settings = settings;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("profiles",    service.findAll());
        model.addAttribute("currentUrl",  settings.getDb().getUrl());
        return "db-profiles/index";
    }

    @PostMapping("/save")
    public String save(
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String url,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String description) {

        if (!name.isEmpty() && !url.isEmpty()) {
            service.save(name.trim(), url.trim(), username.trim(), password.trim(), description.trim());
        }
        return "redirect:/db-profiles";
    }

    /**
     * v4.7.x — #G3 보강: 프론트엔드 (DbProfilesPage) 가 사용하는 form 형식의 저장 엔드포인트.
     *
     * <p>입력: dbType / host / port / dbName / username / password
     * <p>동작: dbType 별로 url 자동 조립 → 기존 service.save() 위임 → JSON 반환
     */
    @PostMapping("/create")
    @ResponseBody
    public java.util.Map<String, Object> create(
            @RequestParam(defaultValue = "")       String name,
            @RequestParam(defaultValue = "oracle") String dbType,
            @RequestParam(defaultValue = "")       String host,
            @RequestParam(defaultValue = "")       String port,
            @RequestParam(defaultValue = "")       String dbName,
            @RequestParam(defaultValue = "")       String username,
            @RequestParam(defaultValue = "")       String password,
            @RequestParam(defaultValue = "")       String description) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        try {
            if (name.trim().isEmpty() || host.trim().isEmpty() || port.trim().isEmpty()) {
                resp.put("success", false);
                resp.put("error",   "name / host / port 필수");
                return resp;
            }
            String url = buildJdbcUrl(dbType.trim(), host.trim(), port.trim(), dbName.trim());
            if (url == null) {
                resp.put("success", false);
                resp.put("error",   "지원하지 않는 dbType: " + dbType);
                return resp;
            }
            DbProfile saved = service.save(name.trim(), url, username.trim(), password, description.trim());
            resp.put("success", true);
            resp.put("id",      saved.getId());
            resp.put("url",     url);
            return resp;
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   "저장 실패: " + e.getMessage());
            return resp;
        }
    }

    /**
     * v4.7.x — #G3 보강: 입력한 DB 정보로 JDBC 연결 테스트.
     *
     * <p>실제 DB 연결을 시도하고 즉시 close. 성공/실패 + 에러 메시지를 JSON 반환.
     */
    @PostMapping("/test")
    @ResponseBody
    public java.util.Map<String, Object> testConnection(
            @RequestParam(defaultValue = "oracle") String dbType,
            @RequestParam(defaultValue = "")       String host,
            @RequestParam(defaultValue = "")       String port,
            @RequestParam(defaultValue = "")       String dbName,
            @RequestParam(defaultValue = "")       String username,
            @RequestParam(defaultValue = "")       String password) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        if (host.trim().isEmpty() || port.trim().isEmpty()) {
            resp.put("success", false);
            resp.put("error",   "host / port 필수");
            return resp;
        }
        String url = buildJdbcUrl(dbType.trim(), host.trim(), port.trim(), dbName.trim());
        if (url == null) {
            resp.put("success", false);
            resp.put("error",   "지원하지 않는 dbType: " + dbType);
            return resp;
        }

        // 드라이버 자동 로드 (Oracle / PG / MySQL — pom 에 의존성 있으면 자동 등록)
        try {
            registerDriverIfNeeded(dbType);
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   "JDBC 드라이버 로드 실패 (" + dbType + "): " + e.getMessage());
            resp.put("url",     url);
            return resp;
        }

        // 연결 시도 — 10초 timeout
        java.util.Properties props = new java.util.Properties();
        if (!username.isEmpty()) props.put("user",     username);
        if (!password.isEmpty()) props.put("password", password);
        // Oracle 의 경우 connectTimeout 은 driver 별로 prop 이름 다름 — 단순화 위해 DriverManager.setLoginTimeout
        java.sql.DriverManager.setLoginTimeout(10);

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, props)) {
            // 검증: SELECT 1 (DBMS 별 호환)
            try (java.sql.Statement st = conn.createStatement()) {
                st.setQueryTimeout(5);
                String pingSql;
                if ("oracle".equalsIgnoreCase(dbType))           pingSql = "SELECT 1 FROM DUAL";
                else if ("mysql".equalsIgnoreCase(dbType))       pingSql = "SELECT 1";
                else if ("postgresql".equalsIgnoreCase(dbType))  pingSql = "SELECT 1";
                else                                              pingSql = "SELECT 1";
                try (java.sql.ResultSet rs = st.executeQuery(pingSql)) {
                    rs.next();
                }
            }
            resp.put("success", true);
            resp.put("url",     url);
            resp.put("message", "연결 성공");
            return resp;
        } catch (java.sql.SQLException e) {
            resp.put("success", false);
            resp.put("error",   "연결 실패: " + e.getMessage()
                    + " (errorCode=" + e.getErrorCode() + ", sqlState=" + e.getSQLState() + ")");
            resp.put("url",     url);
            return resp;
        }
    }

    /**
     * dbType 별 JDBC URL 조립.
     * <ul>
     *   <li>oracle: SID 형식 → {@code jdbc:oracle:thin:@host:port:sid}
     *       <br/>service-name 도 사용 가능 — port 다음 콜론(:) 대신 슬래시(/) 면 service-name 으로 인식하는 드라이버도 있으나
     *       1차 출시는 SID 형식 우선 (오래된 Oracle DB 에서 가장 호환성 높음).</li>
     *   <li>postgresql: {@code jdbc:postgresql://host:port/dbName}</li>
     *   <li>mysql: {@code jdbc:mysql://host:port/dbName}</li>
     * </ul>
     */
    private String buildJdbcUrl(String dbType, String host, String port, String dbName) {
        if ("oracle".equalsIgnoreCase(dbType)) {
            // dbName 이 비어 있으면 default ORCL
            String sid = dbName.isEmpty() ? "ORCL" : dbName;
            return "jdbc:oracle:thin:@" + host + ":" + port + ":" + sid;
        }
        if ("postgresql".equalsIgnoreCase(dbType) || "postgres".equalsIgnoreCase(dbType)) {
            return "jdbc:postgresql://" + host + ":" + port + "/" + (dbName.isEmpty() ? "postgres" : dbName);
        }
        if ("mysql".equalsIgnoreCase(dbType)) {
            return "jdbc:mysql://" + host + ":" + port + "/" + (dbName.isEmpty() ? "mysql" : dbName);
        }
        return null;
    }

    /** 드라이버 명시적 등록 — Spring Boot 환경에서는 자동 등록되지만 안전 측 explicit. */
    private void registerDriverIfNeeded(String dbType) throws Exception {
        if ("oracle".equalsIgnoreCase(dbType))            Class.forName("oracle.jdbc.OracleDriver");
        else if ("postgresql".equalsIgnoreCase(dbType) || "postgres".equalsIgnoreCase(dbType))
                                                          Class.forName("org.postgresql.Driver");
        else if ("mysql".equalsIgnoreCase(dbType))        Class.forName("com.mysql.cj.jdbc.Driver");
    }

    /** AJAX: JSON 방식 프로필 수정 (프론트 편집 모달 → 저장) */
    @PostMapping("/{id}/update-json")
    @ResponseBody
    public java.util.Map<String, Object> updateJson(
            @PathVariable Long id,
            @RequestParam(defaultValue = "")       String name,
            @RequestParam(defaultValue = "oracle") String dbType,
            @RequestParam(defaultValue = "")       String host,
            @RequestParam(defaultValue = "")       String port,
            @RequestParam(defaultValue = "")       String dbName,
            @RequestParam(defaultValue = "")       String username,
            @RequestParam(defaultValue = "")       String password,
            @RequestParam(defaultValue = "")       String description) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        try {
            if (name.trim().isEmpty() || host.trim().isEmpty() || port.trim().isEmpty()) {
                resp.put("success", false);
                resp.put("error",   "name / host / port 필수");
                return resp;
            }
            String url = buildJdbcUrl(dbType.trim(), host.trim(), port.trim(), dbName.trim());
            if (url == null) {
                resp.put("success", false);
                resp.put("error",   "지원하지 않는 dbType: " + dbType);
                return resp;
            }
            service.update(id, name.trim(), url, username.trim(), password, description.trim());
            resp.put("success", true);
            resp.put("id",      id);
            return resp;
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   "수정 실패: " + e.getMessage());
            return resp;
        }
    }

    @PostMapping("/{id}/update")
    public String update(
            @PathVariable Long id,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "") String url,
            @RequestParam(defaultValue = "") String username,
            @RequestParam(defaultValue = "") String password,
            @RequestParam(defaultValue = "") String description) {

        service.update(id, name.trim(), url.trim(), username.trim(), password, description.trim());
        return "redirect:/db-profiles";
    }

    @PostMapping("/{id}/apply")
    public String apply(@PathVariable Long id) {
        service.applyProfile(id);
        return "redirect:/db-profiles?applied=true";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id) {
        service.deleteById(id);
        return "redirect:/db-profiles";
    }

    /** AJAX: get profile detail for edit modal */
    @GetMapping("/{id}/json")
    @ResponseBody
    public java.util.Map<String, String> detail(@PathVariable Long id) {
        java.util.Map<String, String> map = new java.util.LinkedHashMap<String, String>();
        DbProfile p = service.findById(id);
        if (p == null) { map.put("error", "Not found"); return map; }
        map.put("id",          String.valueOf(p.getId()));
        map.put("name",        p.getName());
        map.put("url",         p.getUrl());
        map.put("username",    p.getUsername());
        map.put("description", p.getDescription() != null ? p.getDescription() : "");
        return map;
    }

    /**
     * 저장된 프로필 ID 로 연결 테스트 — 비밀번호는 DB 에 저장된 값 사용.
     */
    @PostMapping("/{id}/test-existing")
    @ResponseBody
    public java.util.Map<String, Object> testExistingConnection(@PathVariable Long id) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        DbProfile p = service.findById(id);
        if (p == null) {
            resp.put("success", false);
            resp.put("error",   "프로필을 찾을 수 없습니다 (id=" + id + ")");
            return resp;
        }

        String url      = p.getUrl();
        String username = p.getUsername();
        String password = p.getPassword();
        String dbType   = inferDbType(url);

        try { registerDriverIfNeeded(dbType); }
        catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   "JDBC 드라이버 로드 실패 (" + dbType + "): " + e.getMessage());
            resp.put("url",     url);
            return resp;
        }

        java.util.Properties props = new java.util.Properties();
        if (username != null && !username.isEmpty()) props.put("user",     username);
        if (password != null && !password.isEmpty()) props.put("password", password);
        java.sql.DriverManager.setLoginTimeout(10);

        try (java.sql.Connection conn = java.sql.DriverManager.getConnection(url, props)) {
            try (java.sql.Statement st = conn.createStatement()) {
                st.setQueryTimeout(5);
                String pingSql = "oracle".equalsIgnoreCase(dbType) ? "SELECT 1 FROM DUAL" : "SELECT 1";
                try (java.sql.ResultSet rs = st.executeQuery(pingSql)) { rs.next(); }
            }
            resp.put("success", true);
            resp.put("url",     url);
            resp.put("message", "연결 성공 (" + p.getName() + ")");
            return resp;
        } catch (java.sql.SQLException e) {
            resp.put("success", false);
            resp.put("error",   "연결 실패: " + e.getMessage()
                    + " (errorCode=" + e.getErrorCode() + ")");
            resp.put("url",     url);
            return resp;
        }
    }

    /** JDBC URL 에서 dbType 추론 */
    private String inferDbType(String url) {
        if (url == null) return "";
        if (url.contains(":oracle:"))     return "oracle";
        if (url.contains(":postgresql:")) return "postgresql";
        if (url.contains(":mysql:"))      return "mysql";
        return "";
    }

    /**
     * v4.7.x — #G3 Live DB Phase 0: 분석 채널 활성/비활성 토글 (ADMIN 전용).
     *
     * <p>SecurityConfig 가 /db-profiles/** 를 ADMIN 으로 제한하므로 추가 권한 검사 없음.
     * 활성화는 사용자 책임 — 이 프로필의 user 가 read-only 권한만 가져야 한다는
     * 명시적 확인.
     */
    @PostMapping("/{id}/live-analysis")
    @ResponseBody
    public java.util.Map<String, Object> toggleLiveAnalysis(
            @PathVariable Long id,
            @RequestParam("enabled") boolean enabled) {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        boolean result = service.toggleLiveAnalysis(id, enabled);
        resp.put("success", true);
        resp.put("id",      id);
        resp.put("enabled", result);
        return resp;
    }

    /**
     * v4.7.x — Live DB 분석에 사용 가능한 활성 프로필 목록 (분석 페이지 dropdown 용).
     * 모든 사용자 read 가능 — 비밀번호 제외 메타만 반환.
     */
    /**
     * v4.7.x — #G3 보강: 메타 정보 (globalEnabled) + 활성 프로필을 wrapper 로 반환.
     * 신규 클라이언트는 이 endpoint 사용 권장. 기존 /active-live 는 호환성 위해 유지.
     */
    @GetMapping("/active-live-status")
    @ResponseBody
    public java.util.Map<String, Object> activeLiveStatus() {
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<String, Object>();
        boolean globalEnabled = liveDbConfig != null && liveDbConfig.isEnabled();
        resp.put("globalEnabled", globalEnabled);
        resp.put("profiles",      activeLiveProfiles());
        return resp;
    }

    @GetMapping("/active-live")
    @ResponseBody
    public java.util.List<java.util.Map<String, Object>> activeLiveProfiles() {
        java.util.List<DbProfile> profiles = service.findActiveLiveAnalysisProfiles();
        java.util.List<java.util.Map<String, Object>> result = new java.util.ArrayList<>();
        for (DbProfile p : profiles) {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<String, Object>();
            m.put("id",          p.getId());
            m.put("name",        p.getName());
            m.put("description", p.getDescription() != null ? p.getDescription() : "");
            m.put("maskedUrl",   p.getMaskedUrl());
            // 비밀번호 / username 은 노출 X (보안)
            // v4.7.x — #G3 보강 B4: 회로 상태 포함 — UI 가 즉시 dropdown 옵션 색상 / disabled 처리
            if (liveDbCircuitBreaker != null) {
                m.put("circuitOpen",                  liveDbCircuitBreaker.isOpen(p.getId()));
                m.put("circuitSecondsUntilHalfOpen",  liveDbCircuitBreaker.secondsUntilHalfOpen(p.getId()));
            } else {
                m.put("circuitOpen",                  false);
                m.put("circuitSecondsUntilHalfOpen",  0L);
            }
            result.add(m);
        }
        return result;
    }
}
