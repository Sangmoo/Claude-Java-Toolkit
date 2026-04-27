package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.ui.config.HostPathTranslator;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.flow.indexer.MiPlatformIndexer;
import io.github.claudetoolkit.ui.flow.indexer.MiPlatformIndexer.MiPlatformScreen;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer.MyBatisStatement;
import io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer;
import io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer.ControllerEndpoint;
import io.github.claudetoolkit.ui.flow.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Phase 1 의 핵심 — 사용자가 입력한 시작점 (테이블 / SP / SQL_ID / MiPlatform XML) 으로부터
 * 데이터 흐름을 단계별로 추적하여 {@link FlowAnalysisResult} 를 반환.
 *
 * <p><b>추적 단계</b> (TABLE 시작 기준 — 가장 흔한 케이스):
 * <ol>
 *   <li>Stage 1 — DML 작성자 탐색
 *       <ul>
 *         <li>MyBatis: {@code byTable} 인덱스 → {@code <insert/update/merge/delete>} statements</li>
 *         <li>DB: {@code ALL_SOURCE} → 해당 테이블에 INSERT/UPDATE 하는 SP/Trigger</li>
 *       </ul>
 *   </li>
 *   <li>Stage 2 — DML 호출자 탐색
 *       <ul><li>각 namespace.id 를 .java 파일에서 grep → DAO/Service 메서드</li></ul>
 *   </li>
 *   <li>Stage 3 — Controller URL 매핑
 *       <ul><li>DAO/Service 메서드명을 callee 로 가진 Controller endpoint 찾기</li></ul>
 *   </li>
 *   <li>Stage 4 — MiPlatform 화면 매칭
 *       <ul><li>Controller URL 을 호출하는 화면 XML 찾기</li></ul>
 *   </li>
 *   <li>Stage 5 — 결과 조립 (nodes/edges/steps)</li>
 * </ol>
 *
 * <p><b>Phase 1 한계</b>: LLM 미사용 — narrative summary 와 mermaid 는 자동 생성 (단순 템플릿).
 * Phase 2 에서 LLM 이 이 result 를 받아 자연어 + mermaid 를 다듬는다.
 */
@Service
public class FlowAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(FlowAnalysisService.class);

    /** Stage 2 grep — Java 파일 최대 스캔 (성능 보호) */
    private static final int MAX_JAVA_SCAN = 20_000;
    /** 한 statement 당 최대 호출자 수 — 같은 SQL 을 여러 DAO 가 부르면 너무 많아짐 */
    private static final int MAX_CALLERS_PER_STATEMENT = 5;
    /** Stage 2.5 — 한 DAO 메서드당 최대 Service 호출자 수 */
    private static final int MAX_SERVICE_CALLERS        = 5;

    private final ToolkitSettings    settings;
    private final MyBatisIndexer     mybatis;
    private final SpringUrlIndexer   spring;
    private final MiPlatformIndexer  miplatform;

    public FlowAnalysisService(ToolkitSettings settings,
                               MyBatisIndexer mybatis,
                               SpringUrlIndexer spring,
                               MiPlatformIndexer miplatform) {
        this.settings   = settings;
        this.mybatis    = mybatis;
        this.spring     = spring;
        this.miplatform = miplatform;
    }

    /** FlowController 의 /source 엔드포인트가 scanPath 를 쓰기 위해 노출. */
    public ToolkitSettings getSettings() { return settings; }

    public FlowAnalysisResult analyze(FlowAnalysisRequest req) {
        long start = System.currentTimeMillis();
        FlowAnalysisResult r = new FlowAnalysisResult();
        r.query = req.getQuery();

        if (req.getQuery() == null || req.getQuery().trim().isEmpty()) {
            r.warnings.add("질문이 비어있습니다.");
            return r;
        }
        String q = req.getQuery().trim();

        // ── 0. target type 판정 ────────────────────────────────────────
        FlowAnalysisRequest.TargetType type = req.getTargetType();
        if (type == FlowAnalysisRequest.TargetType.AUTO) type = autoDetect(q);
        r.targetType = type.name();
        log.info("[Flow] analyze 시작: query='{}' type={}", q, type);

        IdGen ids = new IdGen();

        switch (type) {
            case TABLE:           analyzeFromTable(q, req, r, ids); break;
            case SP:              analyzeFromSp(q, req, r, ids);    break;
            case SQL_ID:          analyzeFromSqlId(q, req, r, ids); break;
            case MIPLATFORM_XML:  analyzeFromUi(q, req, r, ids);    break;
            default:              r.warnings.add("targetType 판정 실패: " + q);
        }

        // ── 통계 / 메타 ────────────────────────────────────────────────
        long elapsed = System.currentTimeMillis() - start;
        r.stats.put("elapsedMs",       elapsed);
        r.stats.put("nodes",           r.nodes.size());
        r.stats.put("edges",           r.edges.size());
        r.stats.put("steps",           r.steps.size());
        r.stats.put("mybatisIndexed",  mybatis.getStatementCount());
        r.stats.put("springEndpoints", spring.getEndpointCount());
        r.stats.put("miplatformScreens", miplatform.getScreenCount());

        // 자동 narrative + mermaid (Phase 2 에서 LLM 으로 대체될 자리)
        r.summary = autoSummary(r);
        r.mermaid = buildMermaid(r);

        log.info("[Flow] analyze 완료: query='{}' nodes={} edges={} steps={} {}ms",
                q, r.nodes.size(), r.edges.size(), r.steps.size(), elapsed);
        return r;
    }

    // ── target type 자동 판정 ───────────────────────────────────────────

    private static final Pattern P_TABLE   = Pattern.compile("^[A-Z][A-Z0-9_]{3,}$");
    private static final Pattern P_SP      = Pattern.compile("^(?:SP|FN|PKG)_[A-Z0-9_]{2,}$|^[A-Z]+\\.(?:SP|FN|PKG)_[A-Z0-9_]{2,}$");
    private static final Pattern P_SQLID   = Pattern.compile("^[a-z][a-zA-Z0-9_.]*\\.[a-z][a-zA-Z0-9_]*$");

    private FlowAnalysisRequest.TargetType autoDetect(String q) {
        String s = q.trim();
        if (s.endsWith(".xml") || s.startsWith("/") || s.contains("/webapp/"))
            return FlowAnalysisRequest.TargetType.MIPLATFORM_XML;
        if (P_SP.matcher(s).matches())
            return FlowAnalysisRequest.TargetType.SP;
        if (P_SQLID.matcher(s).matches())
            return FlowAnalysisRequest.TargetType.SQL_ID;
        if (P_TABLE.matcher(s).matches())
            return FlowAnalysisRequest.TargetType.TABLE;
        // 자연어인 경우 — 안에서 T_* / 대문자 식별자 추출
        java.util.regex.Matcher m = Pattern.compile("\\b(T_[A-Z0-9_]{2,}|[A-Z][A-Z0-9_]{6,})\\b").matcher(s);
        if (m.find()) return FlowAnalysisRequest.TargetType.TABLE;
        return FlowAnalysisRequest.TargetType.TABLE; // 기본
    }

    // ── Stage flows ─────────────────────────────────────────────────────

    private void analyzeFromTable(String query, FlowAnalysisRequest req,
                                  FlowAnalysisResult r, IdGen ids) {
        // 자연어에서 테이블명 추출
        String table = extractTableName(query);
        if (table == null) {
            r.warnings.add("질문에서 테이블명을 추출하지 못함. T_XXX 또는 대문자 식별자 형태로 입력 필요.");
            return;
        }
        r.stats.put("targetTable", table);

        // ── Terminal 노드: TABLE
        FlowNode tableNode = new FlowNode(ids.next(), "table", table)
                .put("desc", "DB 테이블 (분석 대상)");
        r.nodes.add(tableNode);

        // ── Stage 1a: MyBatis 인덱스에서 DML 찾기 (다중 DML 지원)
        Set<FlowAnalysisRequest.DmlFilter> activeDmls = req.getEffectiveDmls();
        Set<String> dmlNames = new HashSet<String>();
        for (FlowAnalysisRequest.DmlFilter d : activeDmls) dmlNames.add(d.name());
        List<MyBatisStatement> mbStatements = mybatis.findStatementsForTable(table, dmlNames);
        int mbHits = mbStatements.size();
        if (mbHits > req.getMaxBranches() * 4) {
            r.warnings.add("MyBatis 매칭 " + mbHits + " 건 — 상위 " + (req.getMaxBranches() * 4) + " 만 분석");
            mbStatements = mbStatements.subList(0, req.getMaxBranches() * 4);
        }
        r.stats.put("mybatisMatches", mbHits);
        log.info("[Flow] Stage1 MyBatis: table={} matches={}", table, mbHits);

        // ── Stage 1b: DB ALL_SOURCE 에서 SP/Trigger 찾기 (다중 DML)
        List<SpHit> sps = req.isIncludeDb() ? findSpsForTable(table, activeDmls) : Collections.<SpHit>emptyList();
        if (sps.size() > req.getMaxBranches() * 2) {
            r.warnings.add("SP 매칭 " + sps.size() + " 건 — 상위 " + (req.getMaxBranches() * 2) + " 만 분석");
            sps = sps.subList(0, req.getMaxBranches() * 2);
        }
        r.stats.put("spMatches", sps.size());

        // ── 각 MyBatis statement 처리: Java caller → Controller → MiPlatform
        int pathIdx = 0;
        for (MyBatisStatement st : mbStatements) {
            FlowNode mbNode = new FlowNode(ids.next(), "mybatis", st.fullId)
                    .put("dml", st.dml).put("snippet", st.snippet);
            mbNode.file = st.file; mbNode.line = st.line;
            r.nodes.add(mbNode);
            r.edges.add(new FlowEdge(mbNode.id, tableNode.id, st.dml));

            // Stage 2: Java 안에서 fullId 문자열 or .shortId( 호출 위치 찾기 (인터페이스 기반 MyBatis 지원)
            List<JavaCaller> callers = grepJavaCallers(st.fullId, MAX_CALLERS_PER_STATEMENT);
            r.stats.merge("javaCallersTotal", (Object) callers.size(),
                    (a, b) -> ((Integer) a) + ((Integer) b));
            log.info("[Flow] Stage2 mybatis={} → callers={} (first={})",
                    st.fullId, callers.size(),
                    callers.isEmpty() ? "none" : (callers.get(0).className + "." + callers.get(0).methodName));

            for (JavaCaller jc : callers) {
                FlowNode daoNode = new FlowNode(ids.next(),
                        jc.guessType(), jc.className + "." + jc.methodName);
                daoNode.file = jc.relPath; daoNode.line = jc.line;
                r.nodes.add(daoNode);
                r.edges.add(new FlowEdge(daoNode.id, mbNode.id,
                        "calls \"" + st.fullId + "\""));

                // Stage 2.5: DAO 메서드를 호출하는 Service/Manager 탐색
                // (Controller 가 DAO 를 직접 호출하는 경우는 드물고 대부분 Service 가 중간 레이어)
                List<JavaCaller> services = grepMethodCallers(
                        jc.methodName, jc.className, MAX_SERVICE_CALLERS);
                Set<String> serviceMethodNames = new LinkedHashSet<String>();
                Map<String, FlowNode> serviceNodes = new LinkedHashMap<String, FlowNode>();
                r.stats.merge("serviceCallersTotal", (Object) services.size(),
                        (a, b) -> ((Integer) a) + ((Integer) b));
                for (JavaCaller svc : services) {
                    FlowNode svcNode = new FlowNode(ids.next(),
                            svc.guessType(), svc.className + "." + svc.methodName);
                    svcNode.file = svc.relPath; svcNode.line = svc.line;
                    r.nodes.add(svcNode);
                    r.edges.add(new FlowEdge(svcNode.id, daoNode.id,
                            "calls " + jc.methodName + "()"));
                    serviceMethodNames.add(svc.methodName);
                    serviceNodes.put(svc.methodName, svcNode);
                }

                // Stage 3: Controller endpoint 매칭
                // (a) Service 가 발견되면 Service.methodName → Controller
                // (b) 항상 DAO.methodName → Controller 도 시도 (Controller 가 DAO 직접 호출하는 legacy 대응)
                Set<String> addedCtrlForJc = new HashSet<String>();
                boolean anyEndpointFound = false;

                for (String svcMethod : serviceMethodNames) {
                    FlowNode parentNode = serviceNodes.get(svcMethod);
                    List<ControllerEndpoint> eps = spring.findByCallee(svcMethod);
                    if (eps.size() > req.getMaxBranches()) {
                        eps = eps.subList(0, req.getMaxBranches());
                    }
                    for (ControllerEndpoint ep : eps) {
                        String key = ep.className + "#" + ep.methodName;
                        if (!addedCtrlForJc.add(key)) continue;
                        anyEndpointFound = true;
                        addControllerAndUi(r, ids, ep, parentNode, svcMethod + "()", req);
                    }
                }

                List<ControllerEndpoint> directEps = spring.findByCallee(jc.methodName);
                if (directEps.size() > req.getMaxBranches()) {
                    directEps = directEps.subList(0, req.getMaxBranches());
                }
                for (ControllerEndpoint ep : directEps) {
                    String key = ep.className + "#" + ep.methodName;
                    if (!addedCtrlForJc.add(key)) continue;
                    anyEndpointFound = true;
                    addControllerAndUi(r, ids, ep, daoNode, jc.methodName + "()", req);
                }

                if (!anyEndpointFound) {
                    r.warnings.add(jc.className + "." + jc.methodName
                            + " 를 호출하는 Controller 미발견 (Service " + services.size() + " 개 경유 포함)");
                }
            }
            pathIdx++;
        }

        // ── SP 노드 추가 (테이블에 직접 연결)
        for (SpHit sp : sps) {
            FlowNode spNode = new FlowNode(ids.next(), "sp", sp.owner + "." + sp.name)
                    .put("type", sp.type).put("snippet", sp.snippet);
            r.nodes.add(spNode);
            r.edges.add(new FlowEdge(spNode.id, tableNode.id,
                    sp.dmlSummary != null ? sp.dmlSummary : "writes"));
        }

        // ── steps 자동 생성 (BFS 역방향 — UI 부터 TABLE 까지)
        r.steps.addAll(buildSteps(r));

        // v4.4.x — ERP/DB 양측 발견 여부를 명시적 플래그로 expose (LLM 이 둘 다 설명하게 강제용)
        r.stats.put("hasErpFlow", !mbStatements.isEmpty());
        r.stats.put("hasDbSpFlow", !sps.isEmpty());
        if (mbStatements.isEmpty() && !sps.isEmpty()) {
            r.warnings.add("ERP 코드 (MyBatis/Java) 에서는 매칭 0건 — DB 오브젝트 (SP/Trigger) 경로만 발견됨.");
        } else if (!mbStatements.isEmpty() && sps.isEmpty()) {
            r.warnings.add("DB 오브젝트 (SP/Trigger) 에서는 매칭 0건 — ERP 코드 경로만 발견됨.");
        } else if (!mbStatements.isEmpty() && !sps.isEmpty()) {
            r.warnings.add("ERP 코드 경로 (" + mbStatements.size() + ") + DB 오브젝트 경로 ("
                    + sps.size() + ") 모두 발견 — 두 경로 모두 데이터를 변경할 수 있음.");
        }
    }

    private void analyzeFromSp(String query, FlowAnalysisRequest req,
                               FlowAnalysisResult r, IdGen ids) {
        r.warnings.add("SP 시작점 분석은 Phase 2 에서 LLM 와 함께 강화 예정 — 현재는 SP 본문만 표시.");
        // 단순: ALL_SOURCE 에서 SP 본문 fetch + 그 안에서 만지는 테이블 추출
        List<SpHit> sps = req.isIncludeDb() ? findSpByName(query) : Collections.<SpHit>emptyList();
        for (SpHit sp : sps) {
            FlowNode spNode = new FlowNode(ids.next(), "sp", sp.owner + "." + sp.name)
                    .put("type", sp.type).put("snippet", sp.snippet);
            r.nodes.add(spNode);
            // 본문에서 INSERT/UPDATE 대상 테이블 추출
            if (sp.tablesTouched != null) {
                for (String t : sp.tablesTouched) {
                    FlowNode tn = new FlowNode(ids.next(), "table", t);
                    r.nodes.add(tn);
                    r.edges.add(new FlowEdge(spNode.id, tn.id, "writes"));
                }
            }
        }
        r.steps.addAll(buildSteps(r));
    }

    private void analyzeFromSqlId(String query, FlowAnalysisRequest req,
                                  FlowAnalysisResult r, IdGen ids) {
        MyBatisStatement st = mybatis.findById(query);
        if (st == null) {
            r.warnings.add("SQL ID '" + query + "' 미발견 (MyBatis 인덱스 외).");
            return;
        }
        // 테이블 추출 후 분석을 TABLE 흐름으로 위임
        if (st.tables != null && !st.tables.isEmpty()) {
            String firstTable = st.tables.iterator().next();
            FlowAnalysisRequest sub = new FlowAnalysisRequest();
            sub.setQuery(firstTable);
            sub.setTargetType(FlowAnalysisRequest.TargetType.TABLE);
            sub.setMaxBranches(req.getMaxBranches());
            sub.setIncludeDb(req.isIncludeDb());
            sub.setIncludeUi(req.isIncludeUi());
            FlowAnalysisResult subR = analyze(sub);
            r.nodes.addAll(subR.nodes);
            r.edges.addAll(subR.edges);
            r.steps.addAll(subR.steps);
            r.warnings.add("SQL_ID 시작점 — 첫 매칭 테이블 " + firstTable + " 기준 분석.");
        }
    }

    private void analyzeFromUi(String query, FlowAnalysisRequest req,
                               FlowAnalysisResult r, IdGen ids) {
        r.warnings.add("MiPlatform XML 시작점 분석은 향후 강화 예정 (Phase 2 LLM).");
        // TODO Phase 2 — UI XML → Controller URL → DAO → MyBatis → TABLE
    }

    // ── DB SP grep (ChatContextEnricher 와 유사 로직, flow 전용 슬림 버전) ──

    /**
     * v4.4.x — 다중 DML 필터 지원. {@code wantSelect} 가 true 면 SELECT 만 하는 SP 도 포함
     * (어떻게 조회되는지 추적용).
     */
    private List<SpHit> findSpsForTable(String table, Set<FlowAnalysisRequest.DmlFilter> dmls) {
        if (table == null || !settings.isDbConfigured()) return Collections.emptyList();
        boolean wantIns = dmls.contains(FlowAnalysisRequest.DmlFilter.INSERT);
        boolean wantUpd = dmls.contains(FlowAnalysisRequest.DmlFilter.UPDATE);
        boolean wantMrg = dmls.contains(FlowAnalysisRequest.DmlFilter.MERGE);
        boolean wantDel = dmls.contains(FlowAnalysisRequest.DmlFilter.DELETE);
        boolean wantSel = dmls.contains(FlowAnalysisRequest.DmlFilter.SELECT);

        List<SpHit> out = new ArrayList<SpHit>();
        String sql =
                "SELECT * FROM ("
              + "  SELECT OWNER, NAME, TYPE, COUNT(*) AS HITS, "
              + "         SUM(CASE WHEN UPPER(TEXT) LIKE '%INSERT%' THEN 1 ELSE 0 END) AS INS, "
              + "         SUM(CASE WHEN UPPER(TEXT) LIKE '%MERGE%'  THEN 1 ELSE 0 END) AS MRG, "
              + "         SUM(CASE WHEN UPPER(TEXT) LIKE '%UPDATE%' THEN 1 ELSE 0 END) AS UPD, "
              + "         SUM(CASE WHEN UPPER(TEXT) LIKE '%DELETE%' THEN 1 ELSE 0 END) AS DEL, "
              + "         SUM(CASE WHEN UPPER(TEXT) LIKE '%SELECT%' THEN 1 ELSE 0 END) AS SEL "
              + "  FROM ALL_SOURCE "
              + "  WHERE UPPER(TEXT) LIKE ? "
              + "    AND TYPE IN ('PROCEDURE','FUNCTION','PACKAGE','PACKAGE BODY','TRIGGER') "
              + "    AND OWNER NOT IN ('SYS','SYSTEM','OUTLN','DBSNMP','MDSYS','CTXSYS','XDB') "
              + "  GROUP BY OWNER, NAME, TYPE "
              + "  ORDER BY HITS DESC"
              + ") WHERE ROWNUM <= 20";
        Connection conn = null;
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            DriverManager.setLoginTimeout(5);
            conn = DriverManager.getConnection(
                    settings.getDb().getUrl(),
                    settings.getDb().getUsername(),
                    settings.getDb().getPassword());
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, "%" + table.toUpperCase() + "%");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                int ins = rs.getInt("INS");
                int mrg = rs.getInt("MRG");
                int upd = rs.getInt("UPD");
                int del = rs.getInt("DEL");
                int sel = rs.getInt("SEL");
                // 활성 DML 중 하나라도 있는 SP 만 채택
                boolean keep = (wantIns && ins > 0) || (wantUpd && upd > 0)
                            || (wantMrg && mrg > 0) || (wantDel && del > 0)
                            || (wantSel && sel > 0);
                if (!keep) continue;
                SpHit h = new SpHit();
                h.owner = rs.getString("OWNER");
                h.name  = rs.getString("NAME");
                h.type  = rs.getString("TYPE");
                List<String> dmlParts = new ArrayList<String>();
                if (ins > 0) dmlParts.add("INSERT");
                if (mrg > 0) dmlParts.add("MERGE");
                if (upd > 0) dmlParts.add("UPDATE");
                if (del > 0) dmlParts.add("DELETE");
                if (sel > 0 && wantSel) dmlParts.add("SELECT");
                h.dmlSummary = String.join("/", dmlParts);
                out.add(h);
            }
            rs.close(); ps.close();
        } catch (Exception e) {
            log.warn("[Flow] SP grep 실패: {}", e.getMessage());
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
        return out;
    }

    private List<SpHit> findSpByName(String query) {
        // 단순 — 정확히 NAME 으로 매칭
        List<SpHit> out = new ArrayList<SpHit>();
        if (!settings.isDbConfigured()) return out;
        Connection conn = null;
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            DriverManager.setLoginTimeout(5);
            conn = DriverManager.getConnection(
                    settings.getDb().getUrl(),
                    settings.getDb().getUsername(),
                    settings.getDb().getPassword());
            String sql = "SELECT OWNER, NAME, TYPE FROM ALL_OBJECTS "
                       + "WHERE OBJECT_NAME = ? "
                       + "  AND OBJECT_TYPE IN ('PROCEDURE','FUNCTION','PACKAGE','TRIGGER') "
                       + "  AND ROWNUM <= 5";
            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setString(1, query.toUpperCase());
            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                SpHit h = new SpHit();
                h.owner = rs.getString("OWNER");
                h.name  = rs.getString("NAME");
                h.type  = rs.getString("TYPE");
                out.add(h);
            }
            rs.close(); ps.close();
        } catch (Exception e) {
            log.warn("[Flow] SP byName 실패: {}", e.getMessage());
        } finally {
            if (conn != null) try { conn.close(); } catch (Exception ignored) {}
        }
        return out;
    }

    // ── Stage 3 helper: Controller 노드 + MiPlatform 화면 노드 추가 ──

    /**
     * Controller endpoint 를 node/edge 로 추가하고, 필요 시 MiPlatform 화면까지 확장.
     * @param parentNode Service (또는 DAO) — Controller 가 {@code calls} 로 가리킬 하위 레이어
     * @param calleeLabel edge label 에 들어갈 "메서드명()" 문자열
     */
    private void addControllerAndUi(FlowAnalysisResult r, IdGen ids,
                                    ControllerEndpoint ep, FlowNode parentNode,
                                    String calleeLabel, FlowAnalysisRequest req) {
        FlowNode ctrlNode = new FlowNode(ids.next(),
                "controller", ep.className + "." + ep.methodName)
                .put("url", ep.url).put("httpMethod", ep.httpMethod);
        ctrlNode.file = ep.file; ctrlNode.line = ep.line;
        r.nodes.add(ctrlNode);
        r.edges.add(new FlowEdge(ctrlNode.id, parentNode.id, "calls " + calleeLabel));

        // Stage 4: MiPlatform 화면 매칭
        if (req.isIncludeUi()) {
            List<MiPlatformScreen> screens = miplatform.findByUrl(ep.url);
            if (screens.isEmpty()) screens = miplatform.findByUrlPartial(ep.url);
            if (screens.size() > req.getMaxBranches()) screens = screens.subList(0, req.getMaxBranches());
            for (MiPlatformScreen sc : screens) {
                FlowNode uiNode = new FlowNode(ids.next(), "ui", sc.title);
                uiNode.file = sc.file;
                r.nodes.add(uiNode);
                r.edges.add(new FlowEdge(uiNode.id, ctrlNode.id, ep.httpMethod + " " + ep.url));
            }
            if (screens.isEmpty()) {
                r.warnings.add("Controller " + ep.url + " 를 호출하는 MiPlatform 화면 미발견");
            }
        }
    }

    // ── Stage 2.5: .methodName( 패턴으로 호출자 Java 파일 grep (Service 레이어용) ──

    /**
     * 주어진 메서드명을 {@code .methodName(} 패턴으로 호출하는 Java 파일을 스캔.
     * Stage 2 의 {@link #grepJavaCallers} 는 "namespace.id" literal 을 찾는데 비해,
     * 이 메서드는 method-call 체인 (Service → DAO 같은) 을 타고 올라가기 위한 범용 grep.
     *
     * @param methodName       찾을 메서드 단순명
     * @param excludeClassName 결과에서 제외할 클래스 (보통 호출대상 자신 — self-reference 루프 방지)
     * @param max              최대 호출자 수
     */
    private List<JavaCaller> grepMethodCallers(String methodName, String excludeClassName, int max) {
        List<JavaCaller> out = new ArrayList<JavaCaller>();
        if (methodName == null || methodName.length() < 3) return out;
        if (settings.getProject() == null) return out;
        String resolved = HostPathTranslator.translate(settings.getProject().getScanPath());
        Path root = Paths.get(resolved);
        if (!Files.isDirectory(root)) return out;

        // 너무 흔한 메서드명 (getX, setX, toString 등) 은 제외 — 노이즈 폭발 방지
        if (isTooCommonMethod(methodName)) return out;

        final String callPattern = "." + methodName + "(";
        final String excludeLc   = excludeClassName == null ? "" : excludeClassName.toLowerCase();
        final List<JavaCaller> hits = out;
        final int[] scanned = { 0 };

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String n = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (n.equals("target") || n.equals("build") || n.equals("node_modules")
                            || n.equals(".git") || n.equals(".idea") || n.equals("test")
                            || n.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (scanned[0]++ > MAX_JAVA_SCAN) return FileVisitResult.TERMINATE;
                    if (hits.size() >= max) return FileVisitResult.TERMINATE;
                    String fname = file.getFileName().toString();
                    if (!fname.endsWith(".java")) return FileVisitResult.CONTINUE;
                    // 제외 대상 클래스와 파일명이 같으면 스킵 (자기 자신 호출 루프 방지)
                    if (!excludeLc.isEmpty()
                            && fname.toLowerCase().equals(excludeLc + ".java")) {
                        return FileVisitResult.CONTINUE;
                    }
                    try {
                        if (Files.size(file) > 2_000_000) return FileVisitResult.CONTINUE;
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        int idx = content.indexOf(callPattern);
                        if (idx < 0) return FileVisitResult.CONTINUE;
                        JavaCaller jc = new JavaCaller();
                        jc.relPath    = root.relativize(file).toString().replace('\\', '/');
                        jc.line       = lineOf(content, idx);
                        jc.className  = guessClassName(content, file);
                        jc.methodName = guessEnclosingMethod(content, idx);
                        if (jc.methodName == null) jc.methodName = "unknownMethod";
                        // 호출자가 Controller 면 Stage 3 에서 어차피 처리되므로 여기선 제외 (중복 방지)
                        if ("controller".equals(jc.guessType())) return FileVisitResult.CONTINUE;
                        hits.add(jc);
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.debug("Java method-caller grep 실패: {}", e.getMessage());
        }
        return hits;
    }

    /** 노이즈 폭발을 일으키는 너무 흔한 이름들 — getter/setter/toString 등. */
    private static boolean isTooCommonMethod(String name) {
        if (name == null) return true;
        String s = name;
        if (s.equals("toString") || s.equals("hashCode") || s.equals("equals")
                || s.equals("init") || s.equals("run") || s.equals("close")
                || s.equals("add") || s.equals("put") || s.equals("get")
                || s.equals("size") || s.equals("isEmpty")) return true;
        // 너무 짧은 메서드는 오매칭 많음
        if (s.length() < 4) return true;
        return false;
    }

    // ── Stage 2: Java 파일에서 namespace.id 호출처 grep ──

    private List<JavaCaller> grepJavaCallers(String fullId, int max) {
        List<JavaCaller> out = new ArrayList<JavaCaller>();
        if (settings.getProject() == null) return out;
        String resolved = HostPathTranslator.translate(settings.getProject().getScanPath());
        Path root = Paths.get(resolved);
        if (!Files.isDirectory(root)) return out;

        final String literal     = "\"" + fullId + "\"";  // SqlSession.selectOne 스타일
        final String shortId     = fullId.substring(fullId.lastIndexOf('.') + 1);
        final String shortLiteral = "\"" + shortId + "\"";
        // 인터페이스 기반 MyBatis (mapper.shortId(...) 호출) — 최신 ERP 코드의 주류
        final String methodCall  = "." + shortId + "(";
        final List<JavaCaller> hits = out;
        final int[] scanned = { 0 };

        try {
            Files.walkFileTree(root, EnumSet.noneOf(FileVisitOption.class), 12, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String n = dir.getFileName() != null ? dir.getFileName().toString() : "";
                    if (n.equals("target") || n.equals("build") || n.equals("node_modules")
                            || n.equals(".git") || n.equals(".idea") || n.equals("test")
                            || n.startsWith(".")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (scanned[0]++ > MAX_JAVA_SCAN) return FileVisitResult.TERMINATE;
                    if (hits.size() >= max) return FileVisitResult.TERMINATE;
                    if (!file.getFileName().toString().endsWith(".java")) return FileVisitResult.CONTINUE;
                    try {
                        if (Files.size(file) > 2_000_000) return FileVisitResult.CONTINUE;
                        String content = new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
                        int idx = content.indexOf(literal);
                        if (idx < 0) idx = content.indexOf(shortLiteral);
                        // 인터페이스 기반 MyBatis: .shortId( 패턴으로 mapper 호출 탐색
                        if (idx < 0) idx = content.indexOf(methodCall);
                        if (idx < 0) return FileVisitResult.CONTINUE;
                        JavaCaller jc = new JavaCaller();
                        jc.relPath    = root.relativize(file).toString().replace('\\', '/');
                        jc.line       = lineOf(content, idx);
                        jc.className  = guessClassName(content, file);
                        jc.methodName = guessEnclosingMethod(content, idx);
                        // 인터페이스 선언부에서 매칭된 경우 enclosing method 가 null → shortId 자체를 caller 메서드명으로 사용
                        // (이렇게 해야 Stage 2.5 가 .shortId( 를 호출하는 Service 를 다시 찾을 수 있음)
                        if (jc.methodName == null) jc.methodName = shortId;
                        hits.add(jc);
                    } catch (Exception ignored) {}
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.debug("Java grep 실패: {}", e.getMessage());
        }
        return hits;
    }

    private static String guessClassName(String content, Path file) {
        java.util.regex.Matcher m = Pattern.compile(
                "public\\s+(?:abstract\\s+)?(?:class|interface)\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(content);
        if (m.find()) return m.group(1);
        String fn = file.getFileName().toString();
        return fn.endsWith(".java") ? fn.substring(0, fn.length() - 5) : fn;
    }

    private static String guessEnclosingMethod(String content, int idx) {
        // idx 이전에서 가장 가까운 메서드 시그니처 찾기
        java.util.regex.Matcher m = Pattern.compile(
                "(?:public|private|protected)\\s+[\\w<>\\[\\],?\\s\\.]+?\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\(",
                Pattern.MULTILINE).matcher(content);
        String last = null;
        while (m.find()) {
            if (m.start() > idx) break;
            last = m.group(1);
        }
        return last;
    }

    private static int lineOf(String content, int idx) {
        int ln = 1;
        for (int i = 0; i < idx && i < content.length(); i++) if (content.charAt(i) == '\n') ln++;
        return ln;
    }

    // ── 자연어 질문에서 테이블명 추출 ──────────────────────────────────

    private static String extractTableName(String q) {
        java.util.regex.Matcher m = Pattern.compile("\\b(T_[A-Z0-9_]{2,}|[A-Z][A-Z0-9_]{6,})\\b").matcher(q);
        return m.find() ? m.group(1) : null;
    }

    // ── steps 자동 생성 (UI → Controller → DAO → MyBatis → TABLE 순) ──

    private List<FlowStep> buildSteps(FlowAnalysisResult r) {
        // 노드를 type 별로 그룹화 후 의미상 흐름 순서로 step 화
        List<FlowStep> out = new ArrayList<FlowStep>();
        int n = 1;
        List<FlowNode> ui    = nodesOfType(r, "ui");
        List<FlowNode> ctrl  = nodesOfType(r, "controller");
        List<FlowNode> svc   = nodesOfType(r, "service");
        List<FlowNode> dao   = nodesOfType(r, "dao");
        List<FlowNode> mb    = nodesOfType(r, "mybatis");
        List<FlowNode> sps   = nodesOfType(r, "sp");
        List<FlowNode> tbls  = nodesOfType(r, "table");

        for (FlowNode u : ui)    out.add(step(n++, "MiPlatform 화면", "사용자가 '" + u.label + "' 화면에서 액션", u));
        for (FlowNode c : ctrl)  out.add(step(n++, "Controller", c.label + " (" + c.meta.getOrDefault("httpMethod","") + " " + c.meta.getOrDefault("url","") + ")", c));
        for (FlowNode s : svc)   out.add(step(n++, "Service", s.label + " 비즈니스 로직 수행", s));
        for (FlowNode d : dao)   out.add(step(n++, "DAO", d.label + " — DB 호출 위임", d));
        for (FlowNode m : mb)    out.add(step(n++, "MyBatis", m.label + " (" + m.meta.getOrDefault("dml","") + ")", m));
        for (FlowNode s : sps)   out.add(step(n++, "Oracle " + s.meta.getOrDefault("type","SP"), s.label + " 실행", s));
        for (FlowNode t : tbls)  out.add(step(n++, "DB 테이블", "최종적으로 " + t.label + " 에 데이터 반영", t));
        return out;
    }

    private static FlowStep step(int no, String actor, String what, FlowNode src) {
        FlowStep s = new FlowStep(no, actor, what);
        s.file = src.file; s.line = src.line;
        return s;
    }

    private static List<FlowNode> nodesOfType(FlowAnalysisResult r, String type) {
        List<FlowNode> out = new ArrayList<FlowNode>();
        for (FlowNode n : r.nodes) if (type.equals(n.type)) out.add(n);
        return out;
    }

    // ── Mermaid 자동 생성 (Phase 2 의 LLM 이 이걸 다듬음) ──

    private static String buildMermaid(FlowAnalysisResult r) {
        if (r.nodes.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("flowchart TD\n");
        for (FlowNode n : r.nodes) {
            String[] shape = shapeOf(n.type);
            String label = mermaidEscape(n.label) + "<br/><small>" + n.type + "</small>";
            // shape[0] / shape[1] 은 "[" / "]", "([" / "])", "[(" / ")]" 같은 1~2 글자 모양 — 통째로 append
            sb.append("  ").append(n.id).append(shape[0])
              .append('"').append(label).append('"')
              .append(shape[1]).append('\n');
        }
        for (FlowEdge e : r.edges) {
            sb.append("  ").append(e.from).append(" -->|")
              .append(mermaidEscape(e.label == null ? "" : e.label))
              .append("| ").append(e.to).append('\n');
        }
        return sb.toString();
    }

    /** 노드 타입별 mermaid 모양 ([..] / (..)등) — [openShape, closeShape] */
    private static String[] shapeOf(String type) {
        if ("ui".equals(type))         return new String[]{"[", "]"};
        if ("controller".equals(type)) return new String[]{"([", "])"};
        if ("service".equals(type))    return new String[]{"[", "]"};
        if ("dao".equals(type))        return new String[]{"[[", "]]"};
        if ("mybatis".equals(type))    return new String[]{"[/", "/]"};
        if ("sp".equals(type))         return new String[]{"[\\", "\\]"};
        if ("table".equals(type))      return new String[]{"[(", ")]"};
        return new String[]{"[", "]"};
    }

    private static String mermaidEscape(String s) {
        if (s == null) return "";
        return s.replace("\"", "&quot;").replace("|", "&#124;");
    }

    private static String autoSummary(FlowAnalysisResult r) {
        if (r.nodes.isEmpty()) return "데이터 흐름을 찾지 못했습니다. 검색어 / scanPath / DB 설정을 확인해주세요.";
        StringBuilder sb = new StringBuilder();
        sb.append("**'").append(r.query).append("'** 추적 결과 — ");
        sb.append("노드 ").append(r.nodes.size()).append("개, ");
        sb.append("엣지 ").append(r.edges.size()).append("개, ");
        sb.append("단계 ").append(r.steps.size()).append("개. ");
        sb.append("(MyBatis ").append(r.stats.getOrDefault("mybatisMatches", 0)).append("건, ");
        sb.append("SP ").append(r.stats.getOrDefault("spMatches", 0)).append("건)");
        return sb.toString();
    }

    // ── 내부 보조 클래스 ────────────────────────────────────────────────

    private static class IdGen {
        private int n = 0;
        String next() { return "n" + (++n); }
    }

    private static class JavaCaller {
        String className;
        String methodName;
        String relPath;
        int    line;

        /** 클래스명 / 경로 휴리스틱으로 type 판정 */
        String guessType() {
            String n = (className == null ? "" : className).toLowerCase();
            String p = (relPath  == null ? "" : relPath).toLowerCase();
            if (n.endsWith("controller") || n.endsWith("restcontroller")
                    || p.contains("/controller/")) return "controller";
            if (n.endsWith("dao") || n.endsWith("mapper") || n.endsWith("repository")
                    || p.contains("/dao/") || p.contains("/mapper/")) return "dao";
            if (n.endsWith("service") || n.endsWith("manager") || n.endsWith("serviceimpl")
                    || p.contains("/service/")) return "service";
            return "service"; // 기본
        }
    }

    private static class SpHit {
        String owner, name, type, dmlSummary, snippet;
        Set<String> tablesTouched;
    }
}
