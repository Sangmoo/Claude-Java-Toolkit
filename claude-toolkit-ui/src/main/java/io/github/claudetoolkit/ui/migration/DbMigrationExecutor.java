package io.github.claudetoolkit.ui.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * DB 자동 마이그레이션 실행 엔진 (v2.9.5).
 *
 * <p>현재 활성 DataSource(대개 H2)에서 데이터를 읽어 타겟 DB로 JDBC Batch 복사.
 * 외래키 의존성 순서 준수, overwrite 옵션, 레코드 수 검증 포함.
 */
@Service
public class DbMigrationExecutor {

    private static final Logger log = LoggerFactory.getLogger(DbMigrationExecutor.class);
    private static final int BATCH_SIZE = 500;

    /** FK 의존성 순서대로 정렬된 테이블 목록 (INSERT 순서) */
    private static final List<String> TABLES_IN_ORDER = Arrays.asList(
        "app_user",
        "user_permission",
        "user_api_usage",       // v4.2.1 — 사용자별 API 사용량 영속화
        "audit_log",
        "review_history",
        "review_comment",
        "review_request",
        "notification",
        "favorites",
        "chat_session",
        "chat_message",
        "analysis_cache",
        "custom_prompt",
        "workspace_history",
        "pipeline_definition",
        "pipeline_execution",
        "pipeline_step_result",
        "db_migration_job",
        "db_profile"            // v2.9.x — DB 프로필 (누락됨)
    );

    private final DataSource                   sourceDataSource;
    private final DbMigrationJobRepository     jobRepo;
    private final DbMigrationStreamBroker      broker;

    public DbMigrationExecutor(DataSource sourceDataSource,
                               DbMigrationJobRepository jobRepo,
                               DbMigrationStreamBroker broker) {
        this.sourceDataSource = sourceDataSource;
        this.jobRepo          = jobRepo;
        this.broker           = broker;
    }

    /** 연결 테스트 (비블로킹) */
    public Map<String, Object> testConnection(String targetType, String host, int port,
                                               String dbName, String user, String password) {
        Map<String, Object> resp = new LinkedHashMap<String, Object>();
        try {
            DriverManagerDataSource target = buildTargetDataSource(targetType, host, port, dbName, user, password);
            try (Connection c = target.getConnection()) {
                DatabaseMetaData md = c.getMetaData();
                resp.put("success",        true);
                resp.put("productName",    md.getDatabaseProductName());
                resp.put("productVersion", md.getDatabaseProductVersion());
            }
        } catch (Exception e) {
            resp.put("success", false);
            resp.put("error",   e.getMessage());
        }
        return resp;
    }

    /** 마이그레이션 시작 — 비동기 실행 */
    public DbMigrationJob start(String targetType, String host, int port,
                                String dbName, String user, String password,
                                boolean overwrite, String username) {
        String maskedUrl = buildMaskedUrl(targetType, host, port, dbName);
        DbMigrationJob job = new DbMigrationJob(targetType, maskedUrl, username, TABLES_IN_ORDER.size());
        jobRepo.save(job);

        final Long jobId = job.getId();
        CompletableFuture.runAsync(() -> {
            runMigration(jobId, targetType, host, port, dbName, user, password, overwrite);
        });

        return job;
    }

    // ── 실제 실행 로직 ────────────────────────────────────────────────────

    private void runMigration(Long jobId, String targetType, String host, int port,
                              String dbName, String user, String password, boolean overwrite) {
        DbMigrationJob job = jobRepo.findById(jobId).orElse(null);
        if (job == null) return;

        try {
            DriverManagerDataSource target = buildTargetDataSource(targetType, host, port, dbName, user, password);

            try (Connection src = sourceDataSource.getConnection();
                 Connection tgt = target.getConnection()) {

                tgt.setAutoCommit(false);

                // 1. 타겟 테이블 존재 여부 체크
                Set<String> existingTables = getExistingTables(tgt);
                List<String> missingTables = new ArrayList<String>();
                for (String tbl : TABLES_IN_ORDER) {
                    if (!existingTables.contains(tbl.toLowerCase())
                            && !existingTables.contains(tbl.toUpperCase())) {
                        missingTables.add(tbl);
                    }
                }
                if (!missingTables.isEmpty()) {
                    throw new IllegalStateException(
                        "타겟 DB에 다음 테이블이 없습니다: " + missingTables + "\n"
                        + "먼저 해당 프로파일로 앱을 1회 기동하여 스키마를 생성하세요."
                    );
                }

                // 2. overwrite=true면 역순으로 DELETE
                if (overwrite) {
                    pushProgress(jobId, "stage", "기존 데이터 삭제 중...");
                    for (int i = TABLES_IN_ORDER.size() - 1; i >= 0; i--) {
                        String tbl = TABLES_IN_ORDER.get(i);
                        try (Statement st = tgt.createStatement()) {
                            st.execute("DELETE FROM " + tbl);
                        } catch (Exception e) {
                            log.warn("[DbMigration] DELETE 실패 (무시): {} - {}", tbl, e.getMessage());
                        }
                    }
                    tgt.commit();
                } else {
                    // overwrite=false면 타겟 DB가 비어있는지 체크
                    for (String tbl : TABLES_IN_ORDER) {
                        long count = countRows(tgt, tbl);
                        if (count > 0) {
                            throw new IllegalStateException(
                                "타겟 DB의 " + tbl + " 테이블에 이미 " + count + "건의 데이터가 있습니다. "
                                + "'덮어쓰기' 옵션을 활성화하거나 수동으로 삭제하세요."
                            );
                        }
                    }
                }

                // 3. 테이블별 순차 복사
                for (String tbl : TABLES_IN_ORDER) {
                    long total = countRows(src, tbl);
                    job.setCurrentTable(tbl, total);
                    jobRepo.save(job);
                    pushProgress(jobId, "table-start",
                            tbl + " (" + total + " rows)");

                    if (total > 0) {
                        copyTable(src, tgt, tbl, jobId, job);
                        tgt.commit();
                    }

                    job.incrementCompletedTables();
                    jobRepo.save(job);
                    pushProgress(jobId, "table-complete",
                            tbl + " - " + total + " rows copied");
                }

                // 4. 검증
                List<String> mismatches = new ArrayList<String>();
                for (String tbl : TABLES_IN_ORDER) {
                    long srcCount = countRows(src, tbl);
                    long tgtCount = countRows(tgt, tbl);
                    if (srcCount != tgtCount) {
                        mismatches.add(tbl + "(source=" + srcCount + ", target=" + tgtCount + ")");
                    }
                }
                if (!mismatches.isEmpty()) {
                    job.setWarnings("레코드 수 불일치: " + String.join(", ", mismatches));
                }

                job.markCompleted();
                jobRepo.save(job);
                pushProgress(jobId, "done", "모든 테이블 이관 완료");

            }
        } catch (Exception e) {
            log.error("[DbMigration] 이관 실패: jobId={}, error={}", jobId, e.getMessage(), e);
            job.markFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            jobRepo.save(job);
            pushProgress(jobId, "error", job.getErrorMessage());
        } finally {
            broker.closeAll(jobId);
        }
    }

    private void copyTable(Connection src, Connection tgt, String tableName,
                           Long jobId, DbMigrationJob job) throws SQLException {
        try (Statement srcStmt = src.createStatement();
             ResultSet rs = srcStmt.executeQuery("SELECT * FROM " + tableName)) {

            ResultSetMetaData meta = rs.getMetaData();
            int colCount = meta.getColumnCount();
            List<String> columns = new ArrayList<String>();
            for (int i = 1; i <= colCount; i++) columns.add(meta.getColumnName(i));

            String insertSql = buildInsertSql(tableName, columns);
            try (PreparedStatement insert = tgt.prepareStatement(insertSql)) {
                int batchCount = 0;
                long doneCount = 0;
                while (rs.next()) {
                    for (int i = 1; i <= colCount; i++) {
                        Object val = rs.getObject(i);
                        insert.setObject(i, val);
                    }
                    insert.addBatch();
                    batchCount++;
                    doneCount++;
                    if (batchCount >= BATCH_SIZE) {
                        insert.executeBatch();
                        batchCount = 0;
                        job.setCurrentTableDone(doneCount);
                        jobRepo.save(job);
                        pushProgress(jobId, "progress", tableName + ": " + doneCount);
                    }
                }
                if (batchCount > 0) {
                    insert.executeBatch();
                    job.setCurrentTableDone(doneCount);
                    jobRepo.save(job);
                }
            }
        }
    }

    private Set<String> getExistingTables(Connection conn) throws SQLException {
        Set<String> tables = new HashSet<String>();
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                tables.add(rs.getString("TABLE_NAME").toLowerCase());
            }
        }
        return tables;
    }

    private long countRows(Connection conn, String tableName) {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            if (rs.next()) return rs.getLong(1);
        } catch (Exception e) {
            // 테이블 없을 수 있음
        }
        return 0;
    }

    private String buildInsertSql(String tableName, List<String> columns) {
        StringBuilder sb = new StringBuilder("INSERT INTO ").append(tableName).append(" (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(columns.get(i));
        }
        sb.append(") VALUES (");
        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append("?");
        }
        sb.append(")");
        return sb.toString();
    }

    private DriverManagerDataSource buildTargetDataSource(String targetType, String host, int port,
                                                           String dbName, String user, String password) {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        String url;
        String driverClass;
        switch (targetType.toLowerCase()) {
            case "postgresql":
                url = String.format("jdbc:postgresql://%s:%d/%s", host, port, dbName);
                driverClass = "org.postgresql.Driver";
                break;
            case "mysql":
                url = String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Seoul&characterEncoding=UTF-8",
                        host, port, dbName);
                driverClass = "com.mysql.cj.jdbc.Driver";
                break;
            case "oracle":
                url = String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, dbName);
                driverClass = "oracle.jdbc.OracleDriver";
                break;
            default:
                throw new IllegalArgumentException("지원하지 않는 DB 유형: " + targetType);
        }
        ds.setDriverClassName(driverClass);
        ds.setUrl(url);
        ds.setUsername(user);
        ds.setPassword(password);
        return ds;
    }

    private String buildMaskedUrl(String targetType, String host, int port, String dbName) {
        return targetType + "://" + host + ":" + port + "/" + dbName;
    }

    private void pushProgress(Long jobId, String event, String message) {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("event",   event);
        payload.put("message", message);
        broker.push(jobId, event, payload);
    }
}
