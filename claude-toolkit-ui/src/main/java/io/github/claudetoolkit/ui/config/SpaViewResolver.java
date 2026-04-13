package io.github.claudetoolkit.ui.config;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.View;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.view.InternalResourceView;

import java.util.Locale;

/**
 * 모든 Thymeleaf 템플릿 뷰 이름을 React SPA(index.html)로 포워딩하는 ViewResolver.
 *
 * 기존 @Controller 가 반환하는 "chat/index", "advisor/index" 등의 뷰 이름을
 * Thymeleaf 대신 /app/index.html (React SPA)로 포워딩합니다.
 *
 * <ul>
 *   <li>@Controller GET 메서드: React SPA 렌더링 (클라이언트 사이드 라우팅)</li>
 *   <li>@ResponseBody / @RestController: 영향 없음 (ViewResolver를 거치지 않음)</li>
 *   <li>redirect: / forward: 접두어: 기존 동작 유지</li>
 * </ul>
 */
@Component
public class SpaViewResolver implements ViewResolver, Ordered {

    @Override
    public View resolveViewName(String viewName, Locale locale) {
        // redirect:, forward: 접두어는 Spring이 직접 처리하므로 null 반환
        if (viewName.startsWith("redirect:") || viewName.startsWith("forward:")) {
            return null;
        }
        // 모든 뷰 이름을 React SPA index.html로 포워딩
        return new InternalResourceView("/app/index.html");
    }

    @Override
    public int getOrder() {
        // 가장 높은 우선순위 — Thymeleaf ViewResolver보다 먼저 실행
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
