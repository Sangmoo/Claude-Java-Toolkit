package io.github.claudetoolkit.docgen.depcheck;

import io.github.claudetoolkit.starter.client.ClaudeClient;

public class DependencyAnalyzerService {

    private static final String SYSTEM_DEPCHECK =
        "당신은 Java/Maven 보안 및 의존성 분석 전문가입니다. pom.xml 내용을 분석하여 다음 형식으로 응답하세요:\n\n" +
        "## 의존성 분석 요약\n" +
        "총 의존성 수, 위험 항목 수 요약\n\n" +
        "## 보안 취약점 의심 항목\n" +
        "| artifactId | 현재 버전 | 권장 버전 | 위험도 | CVE/이슈 |\n" +
        "[SEVERITY: HIGH/MEDIUM/LOW] 형태로 심각도 표시\n\n" +
        "## 버전 업그레이드 권장\n" +
        "| artifactId | 현재 버전 | 최신 안정 버전 | 변경 이유 |\n\n" +
        "## 충돌/중복 의존성\n" +
        "같은 라이브러리 다른 버전이 있거나 불필요한 중복이 있으면 표시\n\n" +
        "## 권장 추가 의존성\n" +
        "프로젝트 스택(Spring Boot 버전 감지)에 맞는 보안/테스트 의존성 추천\n\n" +
        "분석 기준일 기준으로 알고 있는 최신 정보를 활용하고, 모르면 '확인 필요'로 표시하세요.\n" +
        "Spring Boot parent 버전이 있으면 BOM 관리 의존성과 버전 충돌 여부도 확인하세요.";

    private final ClaudeClient claudeClient;

    public DependencyAnalyzerService(ClaudeClient claudeClient) {
        this.claudeClient = claudeClient;
    }

    public String analyze(String pomXml) {
        return claudeClient.chat(SYSTEM_DEPCHECK, pomXml);
    }
}
