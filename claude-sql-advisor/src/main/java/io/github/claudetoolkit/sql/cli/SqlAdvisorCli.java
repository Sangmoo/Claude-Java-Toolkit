package io.github.claudetoolkit.sql.cli;

import io.github.claudetoolkit.sql.advisor.SqlAdvisorService;
import io.github.claudetoolkit.sql.model.AdvisoryResult;
import io.github.claudetoolkit.sql.model.SqlType;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.starter.properties.ClaudeProperties;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.concurrent.Callable;

/**
 * CLI entry point for claude-sql-advisor.
 *
 * <pre>
 * Usage:
 *   java -jar claude-sql-advisor.jar review --file my_proc.sql
 *   java -jar claude-sql-advisor.jar review --file query.sql --output report.md
 * </pre>
 */
@Command(
        name = "claude-sql-advisor",
        mixinStandardHelpOptions = true,
        version = "0.1.0",
        description = "AI-powered SQL & Oracle SP reviewer using Claude API"
)
public class SqlAdvisorCli implements Callable<Integer> {

    @Parameters(index = "0", description = "Command: review")
    private String command;

    @Option(names = {"-f", "--file"}, description = "SQL file to review")
    private File sqlFile;

    @Option(names = {"-o", "--output"}, description = "Output file (Markdown). If omitted, prints to console.")
    private File outputFile;

    @Option(names = {"-t", "--type"}, description = "SQL type: SQL, STORED_PROCEDURE, FUNCTION, TRIGGER, PACKAGE")
    private SqlType sqlType;

    @Option(names = {"--api-key"}, description = "Claude API key (or set CLAUDE_API_KEY env var)")
    private String apiKey;

    @Option(names = {"--model"}, description = "Claude model ID", defaultValue = "claude-sonnet-4-20250514")
    private String model;

    public static void main(String[] args) {
        System.exit(new CommandLine(new SqlAdvisorCli()).execute(args));
    }

    @Override
    public Integer call() throws Exception {
        if (!"review".equalsIgnoreCase(command)) {
            System.err.println("Unknown command: " + command + ". Available: review");
            return 1;
        }

        // Resolve API key
        String resolvedApiKey = apiKey != null ? apiKey : System.getenv("CLAUDE_API_KEY");
        if (resolvedApiKey == null || resolvedApiKey.isEmpty()) {
            System.err.println("ERROR: Claude API key not found. Set CLAUDE_API_KEY env var or use --api-key");
            return 1;
        }

        // Read SQL content
        String sqlContent = readSqlContent();
        if (sqlContent == null) return 1;

        // Run review
        ClaudeProperties props = new ClaudeProperties();
        props.setApiKey(resolvedApiKey);
        props.setModel(model);

        SqlAdvisorService service = new SqlAdvisorService(new ClaudeClient(props));
        AdvisoryResult result = sqlType != null
                ? service.review(sqlContent, sqlType)
                : service.review(sqlContent);

        // Output
        if (outputFile != null) {
            Files.write(outputFile.toPath(), result.toMarkdown().getBytes(StandardCharsets.UTF_8));
            System.out.println("✅ Advisory report saved: " + outputFile.getAbsolutePath());
        } else {
            System.out.println(result.toConsoleOutput());
        }

        return 0;
    }

    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    private String readSqlContent() throws IOException {
        if (sqlFile != null) {
            if (!sqlFile.exists()) {
                System.err.println("ERROR: File not found: " + sqlFile.getAbsolutePath());
                return null;
            }
            return new String(Files.readAllBytes(sqlFile.toPath()), StandardCharsets.UTF_8);
        }

        // Read from stdin if no file specified
        byte[] bytes = readAllBytes(System.in);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
