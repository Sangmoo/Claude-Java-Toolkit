package io.github.claudetoolkit.ui.integration;

import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Git 로컬 저장소 연동 서비스.
 * ProcessBuilder로 git 명령 실행.
 */
@Service
public class GitService {

    /** 최근 커밋 목록 */
    public List<GitCommit> getRecentCommits(String repoPath, int limit) throws IOException {
        String output = exec(repoPath, "git", "log", "--pretty=format:%H|%h|%s|%an|%ai", "-" + limit);
        List<GitCommit> commits = new ArrayList<GitCommit>();
        for (String line : output.split("\n")) {
            String[] parts = line.split("\\|", 5);
            if (parts.length >= 5) {
                commits.add(new GitCommit(parts[0].trim(), parts[1].trim(), parts[2].trim(), parts[3].trim(), parts[4].trim()));
            }
        }
        return commits;
    }

    /** 두 커밋 간 diff */
    public String getDiff(String repoPath, String fromCommit, String toCommit) throws IOException {
        return exec(repoPath, "git", "diff", fromCommit, toCommit);
    }

    /** 단일 커밋의 diff */
    public String getCommitDiff(String repoPath, String commitHash) throws IOException {
        return exec(repoPath, "git", "show", "--format=", commitHash);
    }

    private String exec(String workDir, String... cmd) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(new File(workDir));
        pb.redirectErrorStream(true);
        Process process = pb.start();
        StringBuilder sb = new StringBuilder();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append('\n');
        }
        try { process.waitFor(); } catch (InterruptedException ignored) {}
        return sb.toString();
    }

    public static class GitCommit {
        public final String hash;
        public final String shortHash;
        public final String message;
        public final String author;
        public final String timestamp;

        public GitCommit(String hash, String shortHash, String message, String author, String timestamp) {
            this.hash      = hash;
            this.shortHash = shortHash;
            this.message   = message;
            this.author    = author;
            this.timestamp = timestamp;
        }
    }
}
