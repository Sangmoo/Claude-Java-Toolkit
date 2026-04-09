package io.github.claudetoolkit.ui.integration;

import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * GitHub REST API 연동 서비스.
 * PR diff 가져오기, 코멘트 등록.
 */
@Service
public class GitHubService {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    /** PR diff 가져오기 (unified diff 형식) */
    public String fetchPrDiff(String owner, String repo, int prNumber, String token) throws IOException {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber;
        Request.Builder rb = new Request.Builder().url(url)
                .header("Accept", "application/vnd.github.v3.diff");
        if (token != null && !token.isEmpty()) rb.header("Authorization", "token " + token);
        Response response = client.newCall(rb.build()).execute();
        String body = response.body() != null ? response.body().string() : "";
        response.close();
        if (!response.isSuccessful()) throw new IOException("GitHub API: HTTP " + response.code() + " - " + body.substring(0, Math.min(200, body.length())));
        return body;
    }

    /** PR 설명(description) 가져오기 */
    public String fetchPrDescription(String owner, String repo, int prNumber, String token) throws IOException {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/pulls/" + prNumber;
        Request.Builder rb = new Request.Builder().url(url)
                .header("Accept", "application/vnd.github.v3+json");
        if (token != null && !token.isEmpty()) rb.header("Authorization", "token " + token);
        Response response = client.newCall(rb.build()).execute();
        String body = response.body() != null ? response.body().string() : "";
        response.close();
        if (!response.isSuccessful()) throw new IOException("GitHub API: HTTP " + response.code());
        // 간단 JSON 파싱 (body 필드 추출)
        int idx = body.indexOf("\"body\":");
        if (idx < 0) return "";
        int start = body.indexOf("\"", idx + 7) + 1;
        int end = body.indexOf("\"", start);
        if (end < 0) return "";
        return body.substring(start, end).replace("\\n", "\n").replace("\\\"", "\"");
    }

    /** PR에 코멘트 등록 */
    public void postComment(String owner, String repo, int prNumber, String token, String comment) throws IOException {
        String url = "https://api.github.com/repos/" + owner + "/" + repo + "/issues/" + prNumber + "/comments";
        String payload = "{\"body\":\"" + comment.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
        Request request = new Request.Builder().url(url)
                .header("Authorization", "token " + token)
                .header("Accept", "application/vnd.github.v3+json")
                .post(RequestBody.create(payload, JSON))
                .build();
        Response response = client.newCall(request).execute();
        String body = response.body() != null ? response.body().string() : "";
        response.close();
        if (!response.isSuccessful()) throw new IOException("GitHub 코멘트 등록 실패: HTTP " + response.code());
    }
}
