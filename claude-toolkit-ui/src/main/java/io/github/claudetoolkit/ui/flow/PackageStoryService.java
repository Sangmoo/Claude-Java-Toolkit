package io.github.claudetoolkit.ui.flow;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.flow.indexer.JavaPackageIndexer.JavaClassInfo;
import io.github.claudetoolkit.ui.flow.indexer.MyBatisIndexer.MyBatisStatement;
import io.github.claudetoolkit.ui.flow.indexer.SpringUrlIndexer.ControllerEndpoint;
import io.micrometer.core.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * v4.5 — 패키지 개요의 "📜 스토리" 탭용 Claude 내러티브 생성기.
 *
 * <p>입력: {@link PackageAnalysisService.PackageDetail}
 * <p>출력: 한국어 마크다운 (신입 친화 어조)
 *
 * <p>섹션 고정 구조:
 * <ol>
 *   <li>🎯 이 패키지는 무엇을 하나요?</li>
 *   <li>🏗️ 핵심 구성 요소 (Controller/Service/DAO 계층)</li>
 *   <li>🗄️ 다루는 데이터 (연관 테이블)</li>
 *   <li>🔗 외부 의존</li>
 *   <li>📌 신입이 알면 좋을 포인트</li>
 *   <li>⚠ 주의/추정 (AI 추정 부분 명시)</li>
 * </ol>
 *
 * <p>동작: 동기 호출 (10~30초 소요). 프론트는 로딩 스피너 표시.
 * 결과는 {@link PackageFlowBuilder} 와 마찬가지로 (패키지, 레벨) 키로 간단 캐시.
 */
@Service
public class PackageStoryService {

    private static final Logger log = LoggerFactory.getLogger(PackageStoryService.class);

    private static final int  MAX_ITEMS_PER_TYPE = 30;
    private static final int  STORY_MAX_TOKENS   = 2000;
    private static final long CACHE_TTL_MS       = 30L * 60 * 1000;
    private static final long CLAUDE_TIMEOUT_SEC = 45L;

    private static final String FALLBACK_PROMPT =
            "당신은 한국 ERP 시스템의 시니어 개발자입니다. 신입 개발자에게 Java 패키지의 구조와 역할을 한국어 마크다운으로 친절하게 설명하세요.";

    private final PackageAnalysisService packageService;
    private final ClaudeClient           claudeClient;

    private final Map<String, CacheEntry> cache = new java.util.concurrent.ConcurrentHashMap<String, CacheEntry>();

    private final ExecutorService claudeExecutor = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "package-story-claude");
        t.setDaemon(true);
        return t;
    });

    private static volatile String SYSTEM_PROMPT_CACHED;

    public PackageStoryService(PackageAnalysisService packageService,
                               ClaudeClient claudeClient) {
        this.packageService = packageService;
        this.claudeClient   = claudeClient;
    }

    @Timed(value = "package.story.generate", description = "Time spent generating package story via Claude")
    public StoryResult generate(String packageName, int level, boolean fresh) {
        String cacheKey = packageName + "@L" + level;
        long now = System.currentTimeMillis();

        if (!fresh) {
            CacheEntry e = cache.get(cacheKey);
            if (e != null && (now - e.createdAt) < CACHE_TTL_MS) {
                StoryResult r = new StoryResult();
                r.packageName = packageName;
                r.markdown = e.markdown;
                r.fromCache = true;
                r.cacheAgeMs = now - e.createdAt;
                return r;
            }
        }

        long t0 = System.currentTimeMillis();
        PackageAnalysisService.PackageDetail detail = packageService.getDetail(packageName, level);
        StoryResult result = new StoryResult();
        result.packageName = packageName;

        if (detail == null || detail.classTotal == 0) {
            result.markdown = "## 빈 패키지\n이 패키지에 Java 클래스가 없습니다. Java 인덱스를 먼저 재빌드하세요.";
            return result;
        }

        String sysPrompt = buildSystemPrompt();
        String userMsg   = buildUserMessage(detail);

        Future<String> future = claudeExecutor.submit(() -> claudeClient.chat(sysPrompt, userMsg, STORY_MAX_TOKENS));
        try {
            String md = future.get(CLAUDE_TIMEOUT_SEC, TimeUnit.SECONDS);
            result.markdown = md;
            result.elapsedMs = System.currentTimeMillis() - t0;
            cache.put(cacheKey, new CacheEntry(md, System.currentTimeMillis()));
            log.info("[PackageStory] pkg={} {}ms tokens≈{}",
                    packageName, result.elapsedMs, userMsg.length() / 4);
        } catch (TimeoutException te) {
            future.cancel(true);
            String msg = "Claude 호출 시간 초과 (" + CLAUDE_TIMEOUT_SEC + "s)";
            log.warn("[PackageStory] {} pkg={}", msg, packageName);
            result.markdown = "## ⚠ 스토리 생성 시간 초과\n\n`" + msg + "`\n\n"
                    + "잠시 후 다시 시도하거나 관리자에게 문의하세요.";
            result.error = msg;
        } catch (ExecutionException ee) {
            Throwable cause = ee.getCause() != null ? ee.getCause() : ee;
            log.warn("[PackageStory] Claude 호출 실패 pkg={}: {}", packageName, cause.getMessage());
            result.markdown = "## ⚠ 스토리 생성 실패\n\n`" + cause.getMessage() + "`\n\n"
                    + "재시도하거나 관리자에게 문의하세요.";
            result.error = cause.getMessage();
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            log.warn("[PackageStory] interrupted pkg={}", packageName);
            result.markdown = "## ⚠ 스토리 생성 중단\n\n요청이 중단되었습니다.";
            result.error = "interrupted";
        }
        return result;
    }

    public int clearCache() {
        int n = cache.size();
        cache.clear();
        return n;
    }

    /** v4.5 — 어드민 캐시 통계 엔드포인트용. */
    public int cacheSize() {
        return cache.size();
    }

    // ── Prompt 빌더 ────────────────────────────────────────────────────

    private static String buildSystemPrompt() {
        String cached = SYSTEM_PROMPT_CACHED;
        if (cached != null) return cached;
        try (InputStream in = new ClassPathResource("prompts/package-story.txt").getInputStream()) {
            cached = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            SYSTEM_PROMPT_CACHED = cached;
            return cached;
        } catch (IOException e) {
            log.warn("[PackageStory] prompts/package-story.txt 로드 실패, fallback 사용: {}", e.getMessage());
            SYSTEM_PROMPT_CACHED = FALLBACK_PROMPT;
            return FALLBACK_PROMPT;
        }
    }

    private String buildUserMessage(PackageAnalysisService.PackageDetail d) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 패키지 메타데이터\n\n");
        sb.append("- 패키지명: `").append(d.packageName).append("`\n");
        sb.append("- 총 클래스: ").append(d.classTotal).append("\n");
        sb.append("- 레이어 분포: Controller ").append(d.controllerCount)
          .append(" / Service ").append(d.serviceCount)
          .append(" / DAO ").append(d.daoCount)
          .append(" / Model ").append(d.modelCount).append("\n");
        sb.append("- MyBatis statement: ").append(d.mybatisCount).append("\n");
        sb.append("- 연관 테이블: ").append(d.tableCount).append("\n");
        sb.append("- Controller endpoint: ").append(d.endpointCount).append("\n");

        // Controllers
        if (d.endpoints != null && !d.endpoints.isEmpty()) {
            sb.append("\n## Controller Endpoints (상위 ")
              .append(Math.min(d.endpoints.size(), MAX_ITEMS_PER_TYPE)).append(")\n");
            int i = 0;
            for (ControllerEndpoint ep : d.endpoints) {
                if (i++ >= MAX_ITEMS_PER_TYPE) break;
                sb.append("- `").append(ep.httpMethod).append(" ").append(ep.url)
                  .append("` → `").append(ep.className).append(".").append(ep.methodName).append("`\n");
            }
        }

        // Classes by type
        if (d.classes != null && !d.classes.isEmpty()) {
            sb.append("\n## 클래스 목록 (타입별 상위)\n");
            appendClassesByType(sb, d.classes, "controller", "Controller");
            appendClassesByType(sb, d.classes, "service",    "Service");
            appendClassesByType(sb, d.classes, "dao",        "DAO/Mapper");
            appendClassesByType(sb, d.classes, "model",      "Model/DTO");
        }

        // MyBatis statements
        if (d.mybatisStatements != null && !d.mybatisStatements.isEmpty()) {
            sb.append("\n## MyBatis Statements (상위 ")
              .append(Math.min(d.mybatisStatements.size(), MAX_ITEMS_PER_TYPE)).append(")\n");
            int i = 0;
            for (MyBatisStatement st : d.mybatisStatements) {
                if (i++ >= MAX_ITEMS_PER_TYPE) break;
                sb.append("- [").append(st.dml).append("] `").append(st.fullId).append("`");
                if (st.tables != null && !st.tables.isEmpty()) {
                    sb.append(" → ").append(String.join(", ", st.tables));
                }
                sb.append("\n");
            }
        }

        // Tables
        if (d.tables != null && !d.tables.isEmpty()) {
            sb.append("\n## 연관 테이블 (").append(d.tables.size()).append(")\n");
            sb.append(String.join(", ", d.tables)).append("\n");
        }

        // External deps
        if (d.externalDependencies != null && !d.externalDependencies.isEmpty()) {
            sb.append("\n## 외부 패키지 의존\n");
            for (String dep : d.externalDependencies) {
                sb.append("- `").append(dep).append("`\n");
            }
        }

        sb.append("\n위 데이터를 기반으로 [출력 규칙] 에 따른 신입 친화적 한국어 마크다운을 작성하세요.");
        return sb.toString();
    }

    private static void appendClassesByType(StringBuilder sb, List<JavaClassInfo> classes,
                                            String type, String displayName) {
        List<JavaClassInfo> filtered = new ArrayList<JavaClassInfo>();
        for (JavaClassInfo info : classes) if (type.equals(info.type)) filtered.add(info);
        if (filtered.isEmpty()) return;
        sb.append("### ").append(displayName).append(" (").append(filtered.size()).append(")\n");
        int i = 0;
        for (JavaClassInfo info : filtered) {
            if (i++ >= MAX_ITEMS_PER_TYPE) break;
            sb.append("- `").append(info.className).append("`");
            if (info.relPath != null) sb.append(" — `").append(info.relPath).append("`");
            sb.append("\n");
        }
    }

    // ── DTO / cache ─────────────────────────────────────────────────────

    public static class StoryResult {
        public String  packageName;
        public String  markdown;
        public String  error;
        public boolean fromCache;
        public long    cacheAgeMs;
        public long    elapsedMs;

        public String  getPackageName() { return packageName; }
        public String  getMarkdown()    { return markdown; }
        public String  getError()       { return error; }
        public boolean isFromCache()    { return fromCache; }
        public long    getCacheAgeMs()  { return cacheAgeMs; }
        public long    getElapsedMs()   { return elapsedMs; }
    }

    private static class CacheEntry {
        final String markdown;
        final long   createdAt;
        CacheEntry(String md, long t) { this.markdown = md; this.createdAt = t; }
    }
}
