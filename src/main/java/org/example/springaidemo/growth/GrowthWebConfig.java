package org.example.springaidemo.growth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@org.springframework.scheduling.annotation.EnableScheduling
public class GrowthWebConfig implements WebMvcConfigurer {
    @Override public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
                response.setHeader("Cache-Control", "no-store");
                response.setHeader("X-Content-Type-Options", "nosniff");
                response.setHeader("Referrer-Policy", "no-referrer");
                response.setHeader("X-Frame-Options", "DENY");
                // 不开放 CORS。自定义请求头阻止跨站表单；同时拒绝浏览器声明的跨站请求。
                if (!"GET".equals(request.getMethod()) && !"HEAD".equals(request.getMethod())) {
                    if (!"1".equals(request.getHeader("X-Growth-Request")) ||
                            "cross-site".equals(request.getHeader("Sec-Fetch-Site"))) {
                        throw new GrowthException(HttpStatus.FORBIDDEN, "请从网站页面发起操作");
                    }
                }
                return true;
            }
        }).addPathPatterns("/api/growth/**");
    }
}
