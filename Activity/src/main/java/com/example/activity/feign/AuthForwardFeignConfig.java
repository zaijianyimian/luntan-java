package com.example.activity.feign;

import feign.RequestInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Configuration
public class AuthForwardFeignConfig {
   @Bean
public RequestInterceptor forwardAuthInterceptor() {
    return template -> {
        // 如果是 LongCat 的调用，直接跳过
        String url = template.url();         // 请求路径
        String targetName = template.feignTarget().name();  // FeignClient 的 name

        // 根据 name 过滤，名字你自己对照 ServiceFeign 上的 @FeignClient(name=...)
        if ("longcat-chat".equalsIgnoreCase(targetName)) {
            return;
        }

        ServletRequestAttributes attrs =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return;
        HttpServletRequest req = attrs.getRequest();
        String auth = req.getHeader("Authorization");
        if (auth != null && !auth.isBlank()) {
            template.header("Authorization", auth);
        }
    };
}

}