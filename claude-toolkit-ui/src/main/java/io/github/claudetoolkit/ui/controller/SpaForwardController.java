package io.github.claudetoolkit.ui.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * SPA(React) 라우팅 지원 컨트롤러.
 *
 * 모든 비-API, 비-정적리소스 요청을 app/index.html로 포워딩하여
 * React Router의 클라이언트 사이드 라우팅을 지원한다.
 *
 * 기존 Thymeleaf 컨트롤러(@Controller)와 공존하며,
 * 기존 매핑이 없는 경로만 이 컨트롤러가 처리한다.
 * Spring MVC는 더 구체적인 매핑을 우선 적용하므로
 * 기존 Thymeleaf 엔드포인트는 영향받지 않는다.
 */
@Controller
public class SpaForwardController {

    /**
     * /react/** 경로 — 레거시 호환 (기존 북마크/링크 지원)
     */
    @GetMapping(value = {"/react", "/react/{path:[^\\.]*}", "/react/{path:(?!assets|favicon).*}/**"})
    public String forwardReactLegacy() {
        return "forward:/app/index.html";
    }
}
