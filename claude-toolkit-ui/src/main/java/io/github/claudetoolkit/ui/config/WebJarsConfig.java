package io.github.claudetoolkit.ui.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

/**
 * WebJars 정적 리소스 캐시 헤더 설정 (v2.7.0).
 *
 * <p>WebJars로 번들된 라이브러리({@code /webjars/**})에 대해 1년 캐시를 적용합니다.
 * 브라우저가 파일 내용을 길게 캐시하여 반복 로딩을 방지합니다.
 */
@Configuration
public class WebJarsConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/webjars/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/")
                .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic());
    }
}
