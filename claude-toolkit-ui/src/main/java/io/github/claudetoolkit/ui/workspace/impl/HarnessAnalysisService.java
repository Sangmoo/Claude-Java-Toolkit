package io.github.claudetoolkit.ui.workspace.impl;

import io.github.claudetoolkit.ui.workspace.AnalysisService;
import io.github.claudetoolkit.ui.workspace.AnalysisType;
import io.github.claudetoolkit.ui.workspace.WorkspaceRequest;
import org.springframework.stereotype.Service;

/**
 * 코드 리뷰 하네스 분석 서비스.
 * <p>Analyst → Builder → Reviewer → Verifier 4단계 파이프라인 프롬프트를 빌드합니다.
 * 언어에 따라 Java/Kotlin, SQL, Python/JS/TS 검증 포인트가 분기됩니다.
 */
@Service
public class HarnessAnalysisService implements AnalysisService {

    @Override
    public AnalysisType getType() { return AnalysisType.HARNESS; }

    @Override
    public String buildSystemPrompt(WorkspaceRequest request) {
        String lang      = request.getLanguage() != null ? request.getLanguage() : "java";
        boolean isSql    = "sql".equalsIgnoreCase(lang);
        boolean isPython = "python".equalsIgnoreCase(lang);
        boolean isJs     = "javascript".equalsIgnoreCase(lang) || "typescript".equalsIgnoreCase(lang);
        boolean isKotlin = "kotlin".equalsIgnoreCase(lang);

        String langLabel  = resolveLangLabel(lang);
        String codeBlock  = isSql ? "sql" : lang;

        String verifierSection;
        if (isSql) {
            verifierSection =
                  "**4단계 — 검증자(Verifier)**:\n"
                + "- SQL 문법 오류·괄호·따옴표 불일치 검증\n"
                + "- DROP/TRUNCATE/WHERE 없는 DELETE 등 위험 변경 감지 (HIGH/MEDIUM/LOW)\n"
                + "- Oracle 전용 함수·패키지·시퀀스 의존성 확인\n"
                + "- 최종 판정: VERIFIED / WARNINGS / FAILED\n";
        } else if (isPython) {
            verifierSection =
                  "**4단계 — 검증자(Verifier)**:\n"
                + "- 타입 힌트 누락·잘못된 타입 어노테이션 확인\n"
                + "- 예외 처리 누락·bare except 사용 검출\n"
                + "- 전역 변수·mutable 기본 인자 등 위험 패턴 탐지\n"
                + "- 최종 판정: VERIFIED / WARNINGS / FAILED\n";
        } else if (isJs) {
            verifierSection =
                  "**4단계 — 검증자(Verifier)**:\n"
                + "- async/await 오류 처리 누락·Promise 미반환 검출\n"
                + "- null/undefined 안전성 검사 (?.연산자 누락)\n"
                + "- 메모리 누수 가능성 (이벤트 리스너 미제거 등)\n"
                + "- 최종 판정: VERIFIED / WARNINGS / FAILED\n";
        } else {
            // Java / Kotlin
            verifierSection =
                  "**4단계 — 검증자(Verifier)**:\n"
                + "- 컴파일 가능성: import 누락·타입 불일치·접근 제어자 오류\n"
                + "- 위험 변경 감지: 메서드 시그니처 변경·NPE 추가·리소스 누수 (HIGH/MEDIUM/LOW)\n"
                + "- Spring/JPA 호환성: 순환 의존·N+1·@Transactional 누락\n"
                + "- 최종 판정: VERIFIED / WARNINGS / FAILED\n";
        }

        return "당신은 " + langLabel + " 코드 품질 개선 파이프라인입니다.\n"
             + "다음 4단계 하네스(Harness) 프로세스를 순서대로 실행하세요:\n\n"
             + "**1단계 — 분석가(Analyst)**: 성능 문제, 안티패턴, 가독성 문제, 보안 취약점, 개선 가능 지점을 파악합니다.\n\n"
             + "**2단계 — 개선가(Builder)**: 분석 결과를 토대로 모든 문제를 해결한 개선 코드를 작성합니다.\n\n"
             + "**3단계 — 검토자(Reviewer)**: 변경점을 검증하고 변경 내역·기대 효과·최종 판정을 정리합니다.\n\n"
             + verifierSection
             + "\n반드시 아래 형식으로만 응답하세요:\n\n"
             + "## 📋 분석 요약\n[분석가: 문제점 항목 목록]\n\n"
             + "## 🔧 개선된 코드\n```" + codeBlock + "\n[개선된 전체 코드]\n```\n\n"
             + "## 📝 변경 내역\n[검토자: 변경 사항 항목 목록]\n\n"
             + "## 📈 기대 효과\n[검토자: 성능·가독성·유지보수성 개선 효과]\n\n"
             + "## ✅ 최종 검토 의견\n[검토자: APPROVED/NEEDS_REVISION 판정]\n\n"
             + "## 🏁 검증 판정\n[검증자: VERIFIED/WARNINGS/FAILED + 상세 근거]";
    }

    @Override
    public String buildUserMessage(WorkspaceRequest request) {
        String lang = request.getLanguage() != null ? request.getLanguage() : "java";
        boolean isSql = "sql".equalsIgnoreCase(lang);
        String codeBlock = isSql ? "sql" : lang;
        return "다음 " + resolveLangLabel(lang) + " 코드를 하네스 파이프라인으로 분석해주세요:\n\n"
             + "```" + codeBlock + "\n" + request.getCode() + "\n```";
    }

    private String resolveLangLabel(String lang) {
        if ("sql".equalsIgnoreCase(lang))        return "Oracle SQL";
        if ("kotlin".equalsIgnoreCase(lang))     return "Kotlin";
        if ("python".equalsIgnoreCase(lang))     return "Python";
        if ("javascript".equalsIgnoreCase(lang)) return "JavaScript";
        if ("typescript".equalsIgnoreCase(lang)) return "TypeScript";
        return "Java/Spring";
    }
}
