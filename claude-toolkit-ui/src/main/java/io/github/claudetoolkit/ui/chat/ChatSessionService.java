package io.github.claudetoolkit.ui.chat;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AI 채팅 세션/메시지 관리 서비스 (v2.7.0).
 *
 * <p>세션별 메시지를 H2 DB에 영속화하며, 사용자별 세션 목록 조회/생성/삭제/이름 변경을 제공합니다.
 */
@Service
public class ChatSessionService {

    /** 세션당 최대 턴 수 (user+assistant 쌍) */
    private static final int MAX_TURNS = 20;
    private static final int MAX_MESSAGES = MAX_TURNS * 2;

    private final ChatSessionRepository sessionRepo;
    private final ChatMessageRepository messageRepo;

    public ChatSessionService(ChatSessionRepository sessionRepo,
                              ChatMessageRepository messageRepo) {
        this.sessionRepo = sessionRepo;
        this.messageRepo = messageRepo;
    }

    // ── 세션 목록/조회 ────────────────────────────────────────────────────────

    public List<ChatSession> listByUser(String username) {
        return sessionRepo.findByUsernameOrderByUpdatedAtDesc(username);
    }

    public ChatSession findById(Long id) {
        return sessionRepo.findById(id).orElse(null);
    }

    /** 세션이 특정 사용자의 것인지 확인 (권한 체크용) */
    public boolean isOwner(Long sessionId, String username) {
        ChatSession s = findById(sessionId);
        return s != null && s.getUsername().equals(username);
    }

    // ── 세션 생성/삭제/이름 변경 ────────────────────────────────────────────

    @Transactional
    public ChatSession create(String username, String title) {
        if (title == null || title.trim().isEmpty()) title = "새 대화";
        if (title.length() > 200) title = title.substring(0, 200);
        return sessionRepo.save(new ChatSession(username, title));
    }

    @Transactional
    public void rename(Long sessionId, String newTitle) {
        ChatSession s = sessionRepo.findById(sessionId).orElse(null);
        if (s == null) return;
        if (newTitle == null || newTitle.trim().isEmpty()) newTitle = "새 대화";
        if (newTitle.length() > 200) newTitle = newTitle.substring(0, 200);
        s.setTitle(newTitle.trim());
        s.touch();
        sessionRepo.save(s);
    }

    @Transactional
    public void delete(Long sessionId) {
        messageRepo.deleteBySessionId(sessionId);
        sessionRepo.deleteById(sessionId);
    }

    /** 세션은 유지하되 모든 메시지만 삭제 */
    @Transactional
    public void clearMessages(Long sessionId) {
        messageRepo.deleteBySessionId(sessionId);
        ChatSession s = sessionRepo.findById(sessionId).orElse(null);
        if (s != null) {
            s.touch();
            sessionRepo.save(s);
        }
    }

    // ── 메시지 조회/저장 ──────────────────────────────────────────────────

    /** 세션의 모든 메시지를 role/content Map 리스트로 반환 (Claude API 포맷 호환) */
    public List<Map<String, String>> getMessagesAsMap(Long sessionId) {
        List<ChatMessage> messages = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
        List<Map<String, String>> result = new ArrayList<Map<String, String>>();
        for (ChatMessage m : messages) {
            Map<String, String> map = new LinkedHashMap<String, String>();
            map.put("role",    m.getRole());
            map.put("content", m.getContent());
            result.add(map);
        }
        return result;
    }

    /** 사용자 메시지를 세션에 추가 */
    @Transactional
    public void addUserMessage(Long sessionId, String content) {
        addMessage(sessionId, "user", content);
    }

    /** AI 응답 메시지를 세션에 추가 */
    @Transactional
    public void addAssistantMessage(Long sessionId, String content) {
        addMessage(sessionId, "assistant", content);
    }

    private void addMessage(Long sessionId, String role, String content) {
        if (content == null) content = "";
        messageRepo.save(new ChatMessage(sessionId, role, content));

        // 세션 updatedAt 갱신
        ChatSession s = sessionRepo.findById(sessionId).orElse(null);
        if (s != null) {
            s.touch();
            sessionRepo.save(s);
        }

        // 턴 수 제한 — 오래된 메시지부터 삭제
        long count = messageRepo.countBySessionId(sessionId);
        if (count > MAX_MESSAGES) {
            List<ChatMessage> all = messageRepo.findBySessionIdOrderByCreatedAtAsc(sessionId);
            int toRemove = (int) (count - MAX_MESSAGES);
            for (int i = 0; i < toRemove && i < all.size(); i++) {
                messageRepo.delete(all.get(i));
            }
        }
    }

    /** 세션 제목이 기본값("새 대화")이면 첫 사용자 메시지로부터 자동 생성 */
    @Transactional
    public void autoGenerateTitle(Long sessionId, String firstMessage) {
        ChatSession s = sessionRepo.findById(sessionId).orElse(null);
        if (s == null) return;
        if (!"새 대화".equals(s.getTitle())) return;  // 이미 제목이 있으면 건드리지 않음

        String title = firstMessage == null ? "새 대화" : firstMessage.trim();
        if (title.length() > 30) title = title.substring(0, 30) + "...";
        if (title.isEmpty()) title = "새 대화";
        s.setTitle(title);
        sessionRepo.save(s);
    }
}
