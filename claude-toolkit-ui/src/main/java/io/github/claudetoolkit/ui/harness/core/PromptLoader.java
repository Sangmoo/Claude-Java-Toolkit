package io.github.claudetoolkit.ui.harness.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Phase A — 하네스 stage 프롬프트를 classpath에서 로드합니다.
 *
 * <h3>경로 규약</h3>
 * <pre>
 *   prompts/harness/{harnessName}/{stageName}.md
 *   예) prompts/harness/sp-migration/analyst.md
 * </pre>
 *
 * <p>로드된 프롬프트는 in-memory 캐시되어 반복 호출 시 디스크 I/O가 없습니다.
 * 운영 중 프롬프트 수정 시에는 {@link #clearCache()}로 비워야 반영됩니다.
 *
 * <p>{@link PackageStoryService}의 단일 파일 로드 패턴과 호환되는 일반화 버전입니다.
 */
@Component
public class PromptLoader {

    private static final Logger log = LoggerFactory.getLogger(PromptLoader.class);

    private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<String, String>();

    /**
     * 프롬프트 파일을 로드합니다 — 없으면 {@link IllegalStateException}.
     *
     * @param harnessName "code-review", "sp-migration" 등
     * @param stageName   "analyst", "builder", "reviewer", "verifier" 등
     */
    public String load(String harnessName, String stageName) {
        String content = loadOrNull(harnessName, stageName);
        if (content == null) {
            throw new IllegalStateException(
                    "Prompt file not found: prompts/harness/" + harnessName + "/" + stageName + ".md");
        }
        return content;
    }

    /**
     * 프롬프트 파일을 로드합니다 — 없으면 {@code fallback} 반환.
     * fallback 자체도 캐시되지 않으므로 호출마다 disk를 확인합니다 (없는 경우만).
     */
    public String loadOrDefault(String harnessName, String stageName, String fallback) {
        String content = loadOrNull(harnessName, stageName);
        return content != null ? content : fallback;
    }

    /** 캐시를 비웁니다 — 운영 중 프롬프트 수정 시 호출. */
    public void clearCache() {
        cache.clear();
    }

    /** 디버그용 — 현재 캐시된 프롬프트 개수. */
    public int cacheSize() {
        return cache.size();
    }

    // ── 내부 ──────────────────────────────────────────────────────────────────

    private String loadOrNull(String harnessName, String stageName) {
        validateName(harnessName, "harnessName");
        validateName(stageName,   "stageName");
        String path = "prompts/harness/" + harnessName + "/" + stageName + ".md";
        String cached = cache.get(path);
        if (cached != null) return cached;

        ClassPathResource res = new ClassPathResource(path);
        if (!res.exists()) {
            log.debug("[PromptLoader] not found: {}", path);
            return null;
        }
        try (InputStream in = res.getInputStream()) {
            String content = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
            cache.put(path, content);
            return content;
        } catch (IOException e) {
            log.warn("[PromptLoader] {} 로드 실패: {}", path, e.getMessage());
            return null;
        }
    }

    /**
     * 경로 인젝션 방지 — harnessName/stageName은 classpath 안전 식별자만 허용합니다.
     * "../" 같은 경로 탈출, 절대 경로, 슬래시 등을 차단합니다.
     */
    private static void validateName(String name, String label) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                      || (c >= '0' && c <= '9') || c == '-' || c == '_';
            if (!ok) {
                throw new IllegalArgumentException(
                        label + " contains illegal char '" + c + "': " + name);
            }
        }
    }
}
