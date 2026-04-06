package io.github.claudetoolkit.ui.harness;

import io.github.claudetoolkit.ui.config.ToolkitSettings;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Startup-time cache for:
 *  - Java source files under the configured project scan path
 *  - Oracle DB objects (PROCEDURE / FUNCTION / PACKAGE / TRIGGER)
 *
 * <p>Cache is populated in a background daemon thread after the application
 * context is fully ready ({@link ApplicationReadyEvent}), so it never delays
 * WAS startup. The UI's "캐시 갱신" button can trigger a manual refresh.
 *
 * <p>Thread-safety: {@link CopyOnWriteArrayList} for readers; mutating methods
 * are synchronized on separate locks so concurrent reads are never blocked.
 */
@Service
public class HarnessCacheService {

    private static final int MAX_FILES   = 5_000;
    private static final int MAX_OBJECTS = 3_000;

    private final ToolkitSettings settings;

    // ── File cache ────────────────────────────────────────────────────────────
    private final List<FileEntry>     cachedFiles     = new CopyOnWriteArrayList<FileEntry>();
    private volatile long             lastFileRefresh = 0;
    private final    AtomicBoolean    fileRefreshing  = new AtomicBoolean(false);

    // ── DB cache ──────────────────────────────────────────────────────────────
    private final List<DbObjectEntry> cachedDbObjects  = new CopyOnWriteArrayList<DbObjectEntry>();
    private volatile long             lastDbRefresh    = 0;
    private final    AtomicBoolean    dbRefreshing     = new AtomicBoolean(false);
    /** Whether DB was configured (URL+username+password) at the time of last refresh attempt. */
    private volatile boolean          dbConfigured     = false;
    /** Last DB connection error message, or null if last refresh succeeded / never ran. */
    private volatile String           lastDbError      = null;

    public HarnessCacheService(ToolkitSettings settings) {
        this.settings = settings;
    }

    // ── Startup init (runs after all @PostConstruct beans are ready) ──────────

    @EventListener(ApplicationReadyEvent.class)
    public void initOnStartup() {
        Thread t = new Thread(new Runnable() {
            public void run() {
                refreshFileCache();
                refreshDbCache();
            }
        });
        t.setDaemon(true);
        t.setName("harness-cache-init");
        t.start();
    }

    // ── Public refresh methods ────────────────────────────────────────────────

    /**
     * Scans the project scan path for {@code .java} files and updates the cache.
     * Skips {@code target/}, {@code build/}, {@code .git/}, {@code test/} directories.
     * Capped at {@value #MAX_FILES} files to prevent memory issues on large monorepos.
     */
    public void refreshFileCache() {
        if (!fileRefreshing.compareAndSet(false, true)) return; // already running
        try {
            if (!settings.isProjectConfigured()) {
                cachedFiles.clear();
                lastFileRefresh = System.currentTimeMillis();
                return;
            }
            String scanPath = settings.getProject().getScanPath();
            if (scanPath == null || scanPath.trim().isEmpty()) {
                cachedFiles.clear();
                lastFileRefresh = System.currentTimeMillis();
                return;
            }
            List<FileEntry> files = new ArrayList<FileEntry>();
            scanJavaFiles(new File(scanPath.trim()), new File(scanPath.trim()), files);
            Collections.sort(files, new Comparator<FileEntry>() {
                public int compare(FileEntry a, FileEntry b) {
                    return a.relativePath.compareToIgnoreCase(b.relativePath);
                }
            });
            cachedFiles.clear();
            cachedFiles.addAll(files);
            lastFileRefresh = System.currentTimeMillis();
        } finally {
            fileRefreshing.set(false);
        }
    }

    /**
     * Queries Oracle {@code ALL_OBJECTS} for stored objects and updates the cache.
     * Only valid objects of type PROCEDURE, FUNCTION, PACKAGE, TRIGGER are included.
     * System objects (SYS, SYSTEM, DBMS_%) are filtered out on the DB side.
     */
    public void refreshDbCache() {
        if (!dbRefreshing.compareAndSet(false, true)) return;
        try {
            dbConfigured = settings.isDbConfigured();
            if (!dbConfigured) {
                cachedDbObjects.clear();
                lastDbError   = null;
                lastDbRefresh = System.currentTimeMillis();
                return;
            }
            ToolkitSettings.Db db = settings.getDb();
            List<DbObjectEntry> objects = new ArrayList<DbObjectEntry>();
            Connection conn = null;
            String errorMsg = null;
            try {
                Class.forName("oracle.jdbc.OracleDriver");
                conn = DriverManager.getConnection(
                        db.getUrl(), db.getUsername(), db.getPassword());
                // ROWNUM 서브쿼리 방식 — Oracle 11g 이하에서도 동작
                // (FETCH FIRST N ROWS ONLY 는 Oracle 12c+ 전용이라 ORA-00933 발생)
                String sql =
                    "SELECT * FROM ("
                  + "  SELECT OBJECT_NAME, OBJECT_TYPE, OWNER "
                  + "  FROM ALL_OBJECTS "
                  + "  WHERE OBJECT_TYPE IN ('PROCEDURE','FUNCTION','PACKAGE','TRIGGER') "
                  + "    AND STATUS = 'VALID' "
                  + "    AND OWNER NOT IN ('SYS','SYSTEM','OUTLN','DBSNMP','MDSYS','CTXSYS','XDB',"
                  + "                      'APEX_030200','APEX_040000','FLOWS_FILES') "
                  + "    AND OBJECT_NAME NOT LIKE 'BIN$%' "
                  + "  ORDER BY OWNER, OBJECT_TYPE, OBJECT_NAME"
                  + ") WHERE ROWNUM <= " + MAX_OBJECTS;
                Statement st = conn.createStatement();
                ResultSet rs = st.executeQuery(sql);
                while (rs.next()) {
                    DbObjectEntry e = new DbObjectEntry();
                    e.name  = rs.getString("OBJECT_NAME");
                    e.type  = rs.getString("OBJECT_TYPE");
                    e.owner = rs.getString("OWNER");
                    objects.add(e);
                }
                rs.close();
                st.close();
            } catch (Exception ex) {
                // Capture the error so callers can display a meaningful message
                errorMsg = ex.getMessage() != null ? ex.getMessage() : ex.getClass().getSimpleName();
            } finally {
                if (conn != null) {
                    try { conn.close(); } catch (Exception ignored) {}
                }
            }
            cachedDbObjects.clear();
            cachedDbObjects.addAll(objects);
            lastDbError   = errorMsg;
            lastDbRefresh = System.currentTimeMillis();
        } finally {
            dbRefreshing.set(false);
        }
    }

    // ── Content readers ───────────────────────────────────────────────────────

    /**
     * Reads a Java file's content.
     * Security: path must be under the configured project scan path.
     *
     * @param absolutePath absolute file path
     * @return file content, or {@code null} if not found / not allowed
     */
    public String readFileContent(String absolutePath) {
        if (absolutePath == null || absolutePath.trim().isEmpty()) return null;
        try {
            File f       = new File(absolutePath).getCanonicalFile();
            String scanP = settings.getProject() != null
                    ? settings.getProject().getScanPath() : "";
            if (scanP == null || scanP.trim().isEmpty()) return null;
            File root = new File(scanP.trim()).getCanonicalFile();
            // Security: must be under the scan root
            if (!f.getPath().startsWith(root.getPath())) return null;
            if (!f.exists() || !f.isFile()) return null;

            StringBuilder sb  = new StringBuilder();
            BufferedReader br = null;
            try {
                br = new BufferedReader(
                        new InputStreamReader(new FileInputStream(f), "UTF-8"));
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append('\n');
                }
            } finally {
                if (br != null) { try { br.close(); } catch (Exception ignored) {} }
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Fetches the full source of an Oracle DB object from {@code ALL_SOURCE}.
     *
     * @param name object name (case-insensitive)
     * @param type object type: PROCEDURE / FUNCTION / PACKAGE / TRIGGER
     * @return concatenated source lines, or {@code null} if not found
     */
    public String getDbObjectSource(String name, String type) {
        if (!settings.isDbConfigured()) return null;
        if (name == null || type == null) return null;
        ToolkitSettings.Db db = settings.getDb();
        Connection conn = null;
        try {
            Class.forName("oracle.jdbc.OracleDriver");
            conn = DriverManager.getConnection(
                    db.getUrl(), db.getUsername(), db.getPassword());
            PreparedStatement ps = conn.prepareStatement(
                "SELECT TEXT FROM ALL_SOURCE "
              + "WHERE NAME = ? AND TYPE = ? "
              + "ORDER BY LINE");
            ps.setString(1, name.trim().toUpperCase());
            ps.setString(2, type.trim().toUpperCase());
            ResultSet rs = ps.executeQuery();
            StringBuilder sb = new StringBuilder();
            while (rs.next()) {
                String text = rs.getString("TEXT");
                if (text != null) sb.append(text);
            }
            rs.close();
            ps.close();
            String result = sb.toString().trim();
            return result.isEmpty() ? null : result;
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) { try { conn.close(); } catch (Exception ignored) {} }
        }
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    public List<FileEntry>     getCachedFiles()     { return new ArrayList<FileEntry>(cachedFiles); }
    public List<DbObjectEntry> getCachedDbObjects() { return new ArrayList<DbObjectEntry>(cachedDbObjects); }

    public long    getLastFileRefresh()   { return lastFileRefresh; }
    public long    getLastDbRefresh()     { return lastDbRefresh; }
    public boolean isFileCacheLoaded()    { return lastFileRefresh > 0; }
    public boolean isDbCacheLoaded()      { return lastDbRefresh  > 0; }
    public boolean isFileRefreshing()     { return fileRefreshing.get(); }
    public boolean isDbRefreshing()       { return dbRefreshing.get(); }
    /** Whether DB URL/username/password were configured at last refresh attempt. */
    public boolean isDbConfiguredAtLastRefresh() { return dbConfigured; }
    /** Last DB connection/query error message, or null if last refresh was successful. */
    public String  getLastDbError()       { return lastDbError; }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void scanJavaFiles(File dir, File root, List<FileEntry> result) {
        if (result.size() >= MAX_FILES) return;
        if (!dir.exists() || !dir.isDirectory()) return;
        String dirName = dir.getName();
        if ("target".equals(dirName) || "build".equals(dirName)
                || ".git".equals(dirName)  || "node_modules".equals(dirName)
                || ".idea".equals(dirName) || "out".equals(dirName)) return;

        File[] children = dir.listFiles();
        if (children == null) return;

        for (File child : children) {
            if (result.size() >= MAX_FILES) return;
            if (child.isDirectory()) {
                scanJavaFiles(child, root, result);
            } else if (child.getName().endsWith(".java")) {
                FileEntry e = new FileEntry();
                e.absolutePath = child.getAbsolutePath();
                e.fileName     = child.getName();
                // Relative path from scan root
                String absRoot = root.getAbsolutePath();
                String absFull = child.getAbsolutePath();
                if (absFull.startsWith(absRoot)) {
                    e.relativePath = absFull.substring(absRoot.length())
                            .replace('\\', '/');
                    if (e.relativePath.startsWith("/")) {
                        e.relativePath = e.relativePath.substring(1);
                    }
                } else {
                    e.relativePath = child.getName();
                }
                result.add(e);
            }
        }
    }

    // ── Inner DTOs ────────────────────────────────────────────────────────────

    /** Lightweight descriptor for a Java source file. */
    public static class FileEntry {
        public String absolutePath;
        public String relativePath;
        public String fileName;
    }

    /** Lightweight descriptor for an Oracle DB object. */
    public static class DbObjectEntry {
        public String name;
        public String type;
        public String owner;
    }
}
