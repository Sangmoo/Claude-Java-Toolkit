package io.github.claudetoolkit.ui.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * React SPA 정적 리소스 캐시 설정 (v4.0).
 *
 * <p>Vite 빌드 결과물({@code /assets/**})에 대해 1년 캐시를 적용합니다.
 * Vite가 파일명에 해시를 포함하므로 무효화가 자동 처리됩니다.</p>
 */
@Configuration
public class WebJarsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Vite 빌드 JS/CSS 번들 — 해시 파일명이므로 장기 캐시 안전
        registry.addResourceHandler("/assets/**")
                .addResourceLocations("classpath:/static/assets/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
    }
}
