package io.github.claudetoolkit.ui.controller;

import io.github.claudetoolkit.starter.client.ClaudeClient;
import io.github.claudetoolkit.ui.config.ToolkitSettings;
import io.github.claudetoolkit.ui.harness.HarnessCacheService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * DB 오브젝트 의존성 분석 컨트롤러.
 * 코드/DB 오브젝트 소스에서 호출 관계 · 테이블 의존성을 분석합니다.
 */
@Controller
@RequestMapping("/harness/dependency")
public class HarnessDependencyController {

    private final ClaudeClient        claudeClient;
    private final ToolkitSettings     settings;
    private final HarnessCacheService cacheService;

    public HarnessDependencyController(ClaudeClient claudeClient,
                                       ToolkitSettings settings,
                                       HarnessCacheService cacheService) {
        this.claudeClient = claudeClient;
        this.settings     = settings;
        this.cacheService = cacheService;
    }

    @GetMapping
    public String index(Model model) {
        return "harness/dependency";
    }

    @PostMapping("/analyze")
    @ResponseBody
    public Map<String, Object> analyze(
            @RequestParam("code")                                    String code,
            @RequestParam(value = "language", defaultValue = "sql") String language) {
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        try {
            String system = buildSystem(language);
            String user   = buildUser(code, language);
            String memo   = settings.getProjectContext();
            if (memo != null && !memo.trim().isEmpty()) {
                system = system + "\n\n[프로젝트 컨텍스트]\n" + memo;
            }
            String response = claudeClient.chat(system, user, 4096);
            result.put("success",  true);
            result.put("response", response);
            result.put("language", language);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error",   e.getMessage() != null ? e.getMessage() : "분석 오류");
        }
        return result;
    }

    private String buildSystem(String language) {
        boolean isSql = "sql".equalsIgnoreCase(language);
        if (isSql) {
            return "당신은 Oracle SQL/PL-SQL 의존성 분석 전문가입니다.\n"
                 + "입력된 저장 프로시저·함수·패키지 소스를 분석하여 다음을 파악하세요:\n"
                 + "1. 참조하는 테이블/뷰 목록\n"
                 + "2. 호출하는 다른 프로시저/함수 목록\n"
                 + "3. 사용하는 패키지 목록\n"
                 + "4. 입출력 파라미터 목록\n"
                 + "5. 잠재적인 순환 의존성 위험\n\n"
                 + "반드시 아래 섹션 형식으로 응답하세요:\n\n"
                 + "## 📋 오브젝트 정보\n"
                 + "[오브젝트 유형, 이름, 소유자]\n\n"
                 + "## 📦 테이블/뷰 의존성\n"
                 + "[참조 테이블/뷰 목록, READ/WRITE 구분]\n\n"
                 + "## 🔗 프로시저/함수 의존성\n"
                 + "[호출하는 다른 오브젝트 목록]\n\n"
                 + "## 📥 파라미터 목록\n"
                 + "[IN/OUT/IN-OUT 파라미터와 데이터 타입]\n\n"
                 + "## ⚠️ 주의 사항\n"
                 + "[의존성 관련 위험 요소, 순환 의존성 가능성]\n\n"
                 + "응답은 한국어로 작성하세요.";
        }
        return "당신은 Java 코드 의존성 분석 전문가입니다.\n"
             + "입력된 Java 클래스 소스를 분석하여 다음을 파악하세요:\n"
             + "1. Import된 외부 라이브러리/패키지\n"
             + "2. 주입받는 Spring Bean (의존성)\n"
             + "3. 호출하는 외부 API/서비스\n"
             + "4. 사용하는 DB 쿼리/Repository\n"
             + "5. 강결합 위험 클래스\n\n"
             + "반드시 아래 섹션 형식으로 응답하세요:\n\n"
             + "## 📋 클래스 정보\n"
             + "[클래스명, 패키지, 역할]\n\n"
             + "## 📦 외부 의존성\n"
             + "[라이브러리/프레임워크 의존성 목록]\n\n"
             + "## 🔗 내부 의존성 (Spring Bean)\n"
             + "[주입받는 서비스·레포지터리·컴포넌트 목록]\n\n"
             + "## 🗄️ 데이터 접근 계층\n"
             + "[Repository, JPA Entity, 쿼리 목록]\n\n"
             + "## ⚠️ 설계 위험\n"
             + "[강결합, 단일 책임 원칙 위반 여부]\n\n"
             + "응답은 한국어로 작성하세요.";
    }

    private String buildUser(String code, String language) {
        boolean isSql    = "sql".equalsIgnoreCase(language);
        String langLabel = isSql ? "SQL" : "Java";
        String codeBlock = isSql ? "sql" : "java";
        return "다음 " + langLabel + " 코드의 의존성을 분석하세요:\n\n"
             + "```" + codeBlock + "\n" + code + "\n```";
    }
}
