package io.github.claudetoolkit.ui.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * React SPA 정적 리소스 캐시 헤더 설정 (v4.0.0).
 *
 * <p>React 빌드 결과물({@code /app/**})에 대해 1년 캐시를 적용합니다.
 * Vite가 파일명에 해시를 포함하므로 무효화가 자동 처리됩니다.
 */
@Configuration
public class WebJarsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // React SPA 정적 리소스 (JS/CSS 번들, assets)
        registry.addResourceHandler("/app/**")
                .addResourceLocations("classpath:/static/app/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
    }
}
