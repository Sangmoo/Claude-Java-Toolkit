package io.github.claudetoolkit.ui.integration;

import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Slack / Teams 웹훅 알림 서비스.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client = new OkHttpClient();

    /**
     * Slack 웹훅으로 알림을 전송합니다.
     */
    public boolean sendSlack(String webhookUrl, String title, String body, String color) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) return false;
        String payload = "{\"attachments\":[{\"color\":\"" + escape(color)
                + "\",\"title\":\"" + escape(title)
                + "\",\"text\":\"" + escape(body) + "\"}]}";
        return postJson(webhookUrl, payload, "Slack");
    }

    /**
     * Teams 웹훅으로 알림을 전송합니다.
     */
    public boolean sendTeams(String webhookUrl, String title, String body) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty()) return false;
        String payload = "{\"@type\":\"MessageCard\",\"summary\":\"" + escape(title)
                + "\",\"themeColor\":\"0076D7\""
                + ",\"title\":\"" + escape(title)
                + "\",\"text\":\"" + escape(body) + "\"}";
        return postJson(webhookUrl, payload, "Teams");
    }

    private boolean postJson(String url, String json, String service) {
        try {
            RequestBody reqBody = RequestBody.create(json, JSON);
            Request request = new Request.Builder().url(url).post(reqBody).build();
            Response response = client.newCall(request).execute();
            boolean ok = response.isSuccessful();
            response.close();
            if (!ok) log.warn("[{}] 웹훅 전송 실패: HTTP {}", service, response.code());
            return ok;
        } catch (IOException e) {
            log.error("[{}] 웹훅 전송 오류: {}", service, e.getMessage());
            return false;
        }
    }

    private String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
