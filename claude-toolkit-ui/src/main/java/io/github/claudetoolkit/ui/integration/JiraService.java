package io.github.claudetoolkit.ui.integration;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Base64;

/**
 * Jira REST API v3 연동 서비스.
 */
@Service
public class JiraService {

    private static final Logger log = LoggerFactory.getLogger(JiraService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    /**
     * Jira 이슈 생성.
     * @param baseUrl     Jira 인스턴스 URL (https://your-domain.atlassian.net)
     * @param email       Jira 계정 이메일
     * @param apiToken    Jira API 토큰
     * @param projectKey  프로젝트 키 (예: "PROJ")
     * @param issueType   이슈 타입 (예: "Bug", "Task")
     * @param summary     이슈 제목
     * @param description 이슈 설명
     */
    public String createIssue(String baseUrl, String email, String apiToken,
                              String projectKey, String issueType,
                              String summary, String description) throws IOException {
        String url = baseUrl.replaceAll("/+$", "") + "/rest/api/3/issue";
        String auth = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes());

        String descEsc = escape(description);
        String payload = "{"
                + "\"fields\":{"
                + "\"project\":{\"key\":\"" + escape(projectKey) + "\"},"
                + "\"issuetype\":{\"name\":\"" + escape(issueType) + "\"},"
                + "\"summary\":\"" + escape(summary) + "\","
                + "\"description\":{\"type\":\"doc\",\"version\":1,\"content\":[{\"type\":\"paragraph\",\"content\":[{\"type\":\"text\",\"text\":\"" + descEsc + "\"}]}]}"
                + "}}";

        Request request = new Request.Builder().url(url)
                .header("Authorization", "Basic " + auth)
                .header("Accept", "application/json")
                .post(RequestBody.create(payload, JSON))
                .build();

        Response response = client.newCall(request).execute();
        String body = response.body() != null ? response.body().string() : "";
        response.close();
        if (!response.isSuccessful()) {
            throw new IOException("Jira 이슈 생성 실패: HTTP " + response.code() + " - " + body.substring(0, Math.min(300, body.length())));
        }
        // key 추출
        int idx = body.indexOf("\"key\":\"");
        if (idx >= 0) {
            int start = idx + 7;
            int end = body.indexOf("\"", start);
            return body.substring(start, end);
        }
        return "created";
    }

    /** 연결 테스트 */
    public boolean testConnection(String baseUrl, String email, String apiToken) {
        try {
            String url = baseUrl.replaceAll("/+$", "") + "/rest/api/3/myself";
            String auth = Base64.getEncoder().encodeToString((email + ":" + apiToken).getBytes());
            Request request = new Request.Builder().url(url)
                    .header("Authorization", "Basic " + auth)
                    .header("Accept", "application/json")
                    .build();
            Response response = client.newCall(request).execute();
            boolean ok = response.isSuccessful();
            response.close();
            return ok;
        } catch (Exception e) {
            log.error("[Jira] 연결 테스트 실패: {}", e.getMessage());
            return false;
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
