package io.github.claudetoolkit.docgen.scanner;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Scans a Spring MVC project directory and collects source files
 * (Controller, Service, Repository, Mapper, DTO, XML mappers, SQL)
 * to be used as context for Claude's documentation generation.
 *
 * <p>Excludes: target/, test/, build/ directories to keep context relevant.
 */
public class ProjectScannerService {

    /** Maximum total characters included in the context to avoid token overflow. */
    private static final int MAX_CONTEXT_CHARS = 80_000;

    private static final List<String> EXCLUDE_DIRS = Arrays.asList(
        "/target/", "\\target\\",
        "/test/",   "\\test\\",
        "/build/",  "\\build\\",
        "/.git/",   "\\.git\\"
    );

    /**
     * Walks the project tree and returns classified source files.
     *
     * @param rootPath absolute path to the Spring project root
     * @throws IOException if the path does not exist or is not a directory
     */
    public List<ScannedFile> scanProject(String rootPath) throws IOException {
        Path root = Paths.get(rootPath);
        if (!Files.exists(root) || !Files.isDirectory(root)) {
            throw new IOException("경로가 존재하지 않거나 디렉토리가 아닙니다: " + rootPath);
        }

        List<ScannedFile> files = new ArrayList<>();

        try (Stream<Path> stream = Files.walk(root)) {
            stream
                .filter(Files::isRegularFile)
                .filter(p -> !isExcluded(p.toString()))
                .filter(p -> isTargetExtension(p.toString()))
                .forEach(p -> {
                    try {
                        String content = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                        String type = detectType(p.toString(), content);
                        String relative = root.relativize(p).toString();
                        files.add(new ScannedFile(relative, type, content));
                    } catch (IOException ignored) {}
                });
        }

        files.sort(Comparator.comparingInt(f -> typeOrder(f.getType())));
        return files;
    }

    /**
     * Builds a formatted Markdown context string from scanned files.
     * Content is capped at {@value #MAX_CONTEXT_CHARS} characters.
     */
    public String buildContext(List<ScannedFile> files) {
        StringBuilder sb = new StringBuilder();
        sb.append("## Scanned Spring MVC Project Files\n\n");
        sb.append("The following source files were found in the project:\n\n");

        Map<String, List<ScannedFile>> byType = new LinkedHashMap<>();
        for (ScannedFile f : files) {
            byType.computeIfAbsent(f.getType(), k -> new ArrayList<>()).add(f);
        }

        int totalChars = 0;
        outer:
        for (Map.Entry<String, List<ScannedFile>> entry : byType.entrySet()) {
            sb.append("### ").append(entry.getKey()).append("\n\n");
            for (ScannedFile f : entry.getValue()) {
                if (totalChars >= MAX_CONTEXT_CHARS) {
                    sb.append("_(컨텍스트 용량 제한으로 이하 파일 생략)_\n");
                    break outer;
                }
                String fence = f.getRelativePath().endsWith(".xml") ? "xml" : "java";
                sb.append("**`").append(f.getRelativePath()).append("`**\n");
                sb.append("```").append(fence).append("\n")
                  .append(f.getContent())
                  .append("\n```\n\n");
                totalChars += f.getContent().length();
            }
        }
        return sb.toString();
    }

    /**
     * Returns a short human-readable summary: e.g. "Controller: 3개  Service: 2개"
     */
    public String getScanSummary(List<ScannedFile> files) {
        Map<String, Long> counts = files.stream()
            .collect(Collectors.groupingBy(ScannedFile::getType, Collectors.counting()));
        return counts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + ": " + e.getValue() + "개")
            .collect(Collectors.joining("  "));
    }

    // ── private helpers ──────────────────────────────────────────────────────

    private boolean isExcluded(String path) {
        for (String dir : EXCLUDE_DIRS) {
            if (path.contains(dir)) return true;
        }
        return false;
    }

    private boolean isTargetExtension(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".xml") || lower.endsWith(".sql");
    }

    private String detectType(String path, String content) {
        String lower = path.toLowerCase();

        if (lower.endsWith(".sql")) return "SQL";

        if (lower.endsWith(".xml")) {
            if (content.contains("<mapper") || content.contains("<select") || content.contains("<insert"))
                return "MyBatis Mapper XML";
            return "XML";
        }

        // Java annotation-based detection (most specific first)
        if (content.contains("@RestController") || content.contains("@Controller")) return "Controller";
        if (content.contains("@Repository") || lower.contains("repository") || lower.contains("dao")) return "Repository/DAO";
        if (content.contains("@Mapper") || lower.contains("mapper")) return "Mapper";
        if (lower.contains("serviceimpl") || (content.contains("@Service") && lower.contains("impl"))) return "ServiceImpl";
        if (content.contains("@Service") || lower.contains("service")) return "Service";
        if (content.contains("@Configuration") || lower.contains("config")) return "Config";
        if (lower.contains("dto") || lower.contains("vo") || lower.contains("request") || lower.contains("response")) return "DTO/VO";
        return "Java";
    }

    private int typeOrder(String type) {
        switch (type) {
            case "Controller":        return 1;
            case "Service":           return 2;
            case "ServiceImpl":       return 3;
            case "Repository/DAO":    return 4;
            case "Mapper":            return 5;
            case "MyBatis Mapper XML":return 6;
            case "DTO/VO":            return 7;
            case "SQL":               return 8;
            case "Config":            return 9;
            default:                  return 10;
        }
    }
}
