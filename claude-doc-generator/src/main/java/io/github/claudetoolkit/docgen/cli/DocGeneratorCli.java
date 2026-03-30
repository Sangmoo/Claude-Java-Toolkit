package io.github.claudetoolkit.docgen.cli;

import io.github.claudetoolkit.docgen.generator.DocGeneratorService;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;

/**
 * CLI entry point for claude-doc-generator.
 *
 * <pre>
 * Usage:
 *   java -jar claude-doc-generator.jar generate --file SP_MY_PROC.sql --format md
 *   java -jar claude-doc-generator.jar generate --file SP_MY_PROC.sql --format typst --output docs/SP_MY_PROC.typ
 * </pre>
 */
@Command(
        name = "claude-doc-generator",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "Auto-generate technical docs from Oracle SP / Java source using Claude API"
)
public class DocGeneratorCli implements Callable<Integer> {

    @Parameters(index = "0", description = "Command: generate")
    private String command;

    @Option(names = {"-f", "--file"}, required = true, description = "Source file to document")
    private File sourceFile;

    @Option(names = {"--format"}, description = "Output format: md (default) or typst", defaultValue = "md")
    private String format;

    @Option(names = {"-o", "--output"}, description = "Output file path. If omitted, prints to console.")
    private File outputFile;

    @Option(names = {"-t", "--type"}, description = "Source type hint (e.g. 'Oracle Stored Procedure', 'Java Service')")
    private String sourceType;

    @Option(names = {"--api-key"}, description = "Claude API key (or set CLAUDE_API_KEY env var)")
    private String apiKey;

    @Option(names = {"--model"}, description = "Claude model ID", defaultValue = "claude-sonnet-4-20250514")
    private String model;

    public static void main(String[] args) {
        System.exit(new CommandLine(new DocGeneratorCli()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        if (!"generate".equalsIgnoreCase(command)) {
            System.err.println("Unknown command: " + command + ". Available: generate");
            return 1;
        }

        String resolvedApiKey = apiKey != null ? apiKey : System.getenv("CLAUDE_API_KEY");
        if (resolvedApiKey == null || resolvedApiKey.isEmpty()) {
            System.err.println("ERROR: Claude API key not found. Set CLAUDE_API_KEY env var or use --api-key");
            return 1;
        }

        if (!sourceFile.exists()) {
            System.err.println("ERROR: File not found: " + sourceFile.getAbsolutePath());
            return 1;
        }

        String sourceCode = new String(Files.readAllBytes(sourceFile.toPath()), StandardCharsets.UTF_8);
        String detectedType = sourceType != null ? sourceType : detectType(sourceFile.getName());

        ClaudeProperties props = new ClaudeProperties();
        props.setApiKey(resolvedApiKey);
        props.setModel(model);

        DocGeneratorService service = new DocGeneratorService(new ClaudeClient(props));

        String result;
        if ("typst".equalsIgnoreCase(format)) {
            result = service.generateTypst(sourceCode, detectedType);
        } else {
            result = service.generateMarkdown(sourceCode, detectedType);
        }

        if (outputFile != null) {
            Files.write(outputFile.toPath(), result.getBytes(StandardCharsets.UTF_8));
            System.out.println("✅ Documentation saved: " + outputFile.getAbsolutePath());
        } else {
            System.out.println(result);
        }

        return 0;
    }

    private String detectType(String filename) {
        String upper = filename.toUpperCase();
        if (upper.endsWith(".SQL")) return "Oracle Stored Procedure / SQL";
        if (upper.endsWith(".JAVA")) return "Java Source";
        if (upper.endsWith(".XML")) return "iBatis/MyBatis Mapper XML";
        return "Source Code";
    }
}
