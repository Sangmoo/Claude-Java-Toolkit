package io.github.claudetoolkit.ui.flow.indexer;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * v4.5 — JavaPackageIndexer · MyBatisCallerIndex 공통 스캔 한도.
 *
 * <p>application.yml 의 {@code toolkit.indexer.*} 로 외부화.
 * 미설정 시 기본값은 기존 하드코딩 상수와 동일 → 동작 무변경.
 *
 * <pre>
 * toolkit:
 *   indexer:
 *     max-java-scan: 30000
 *     max-file-size: 2000000
 * </pre>
 */
@Configuration
@ConfigurationProperties(prefix = "toolkit.indexer")
public class IndexerConfig {

    /** Java 파일 단일 스캔 시 최대 방문 파일 수. 0 또는 음수 → 무제한. */
    private int maxJavaScan = 30_000;

    /** 단일 파일이 이 크기(바이트)를 초과하면 스킵. */
    private long maxFileSize = 2_000_000L;

    public int getMaxJavaScan() {
        return maxJavaScan;
    }

    public void setMaxJavaScan(int maxJavaScan) {
        this.maxJavaScan = maxJavaScan;
    }

    public long getMaxFileSize() {
        return maxFileSize;
    }

    public void setMaxFileSize(long maxFileSize) {
        this.maxFileSize = maxFileSize;
    }
}
