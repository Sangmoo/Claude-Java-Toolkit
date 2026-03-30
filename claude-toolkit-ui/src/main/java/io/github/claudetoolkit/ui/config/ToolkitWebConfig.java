package io.github.claudetoolkit.ui.config;

import io.github.claudetoolkit.docgen.apispec.ApiSpecGeneratorService;
import io.github.claudetoolkit.docgen.codereview.CodeReviewService;
import io.github.claudetoolkit.docgen.commitmsg.CommitMsgService;
import io.github.claudetoolkit.docgen.complexity.ComplexityAnalyzerService;
import io.github.claudetoolkit.docgen.converter.CodeConverterService;
import io.github.claudetoolkit.docgen.generator.DocGeneratorService;
import io.github.claudetoolkit.docgen.loganalyzer.LogAnalyzerService;
import io.github.claudetoolkit.docgen.regex.RegexGeneratorService;
import io.github.claudetoolkit.docgen.scanner.ProjectScannerService;
import io.github.claudetoolkit.docgen.testgen.TestGeneratorService;
import io.github.claudetoolkit.sql.advisor.SqlAdvisorService;
import io.github.claudetoolkit.sql.db.OracleMetaService;
import io.github.claudetoolkit.sql.erd.ErdAnalyzerService;
import io.github.claudetoolkit.sql.migration.MigrationScriptService;
import io.github.claudetoolkit.sql.mockdata.MockDataGeneratorService;
import io.github.claudetoolkit.starter.client.ClaudeClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers all module service beans for the UI layer.
 */
@Configuration
public class ToolkitWebConfig {

    // ── Existing beans ────────────────────────────────────────────────────────

    @Bean
    public SqlAdvisorService sqlAdvisorService(ClaudeClient claudeClient) {
        return new SqlAdvisorService(claudeClient);
    }

    @Bean
    public DocGeneratorService docGeneratorService(ClaudeClient claudeClient) {
        return new DocGeneratorService(claudeClient);
    }

    @Bean
    public OracleMetaService oracleMetaService() {
        return new OracleMetaService();
    }

    @Bean
    public ProjectScannerService projectScannerService() {
        return new ProjectScannerService();
    }

    // ── New beans ─────────────────────────────────────────────────────────────

    @Bean
    public CodeConverterService codeConverterService(ClaudeClient claudeClient) {
        return new CodeConverterService(claudeClient);
    }

    @Bean
    public TestGeneratorService testGeneratorService(ClaudeClient claudeClient) {
        return new TestGeneratorService(claudeClient);
    }

    @Bean
    public ApiSpecGeneratorService apiSpecGeneratorService(ClaudeClient claudeClient) {
        return new ApiSpecGeneratorService(claudeClient);
    }

    @Bean
    public ErdAnalyzerService erdAnalyzerService(ClaudeClient claudeClient) {
        return new ErdAnalyzerService(claudeClient);
    }

    @Bean
    public CodeReviewService codeReviewService(ClaudeClient claudeClient) {
        return new CodeReviewService(claudeClient);
    }

    @Bean
    public ComplexityAnalyzerService complexityAnalyzerService(ClaudeClient claudeClient) {
        return new ComplexityAnalyzerService(claudeClient);
    }

    @Bean
    public MockDataGeneratorService mockDataGeneratorService(ClaudeClient claudeClient) {
        return new MockDataGeneratorService(claudeClient);
    }

    @Bean
    public MigrationScriptService migrationScriptService(ClaudeClient claudeClient) {
        return new MigrationScriptService(claudeClient);
    }

    // ── v3 beans ──────────────────────────────────────────────────────────────

    @Bean
    public LogAnalyzerService logAnalyzerService(ClaudeClient claudeClient) {
        return new LogAnalyzerService(claudeClient);
    }

    @Bean
    public RegexGeneratorService regexGeneratorService(ClaudeClient claudeClient) {
        return new RegexGeneratorService(claudeClient);
    }

    @Bean
    public CommitMsgService commitMsgService(ClaudeClient claudeClient) {
        return new CommitMsgService(claudeClient);
    }

    // ── v0.5-v0.7 beans ───────────────────────────────────────────────────────

    @Bean
    public io.github.claudetoolkit.docgen.javadoc.JavadocGeneratorService javadocGeneratorService(ClaudeClient claudeClient) {
        return new io.github.claudetoolkit.docgen.javadoc.JavadocGeneratorService(claudeClient);
    }

    @Bean
    public io.github.claudetoolkit.docgen.refactoring.RefactoringService refactoringService(ClaudeClient claudeClient) {
        return new io.github.claudetoolkit.docgen.refactoring.RefactoringService(claudeClient);
    }

    @Bean
    public io.github.claudetoolkit.docgen.depcheck.DependencyAnalyzerService dependencyAnalyzerService(ClaudeClient claudeClient) {
        return new io.github.claudetoolkit.docgen.depcheck.DependencyAnalyzerService(claudeClient);
    }

    @Bean
    public io.github.claudetoolkit.docgen.masking.DataMaskingService dataMaskingService(ClaudeClient claudeClient) {
        return new io.github.claudetoolkit.docgen.masking.DataMaskingService(claudeClient);
    }

    @Bean
    public io.github.claudetoolkit.docgen.migration.SpringMigrationService springMigrationService(ClaudeClient claudeClient) {
        return new io.github.claudetoolkit.docgen.migration.SpringMigrationService(claudeClient);
    }
}
