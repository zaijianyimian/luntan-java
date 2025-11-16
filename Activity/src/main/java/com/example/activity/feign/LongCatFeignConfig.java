package com.example.activity.feign;

import feign.RequestInterceptor;
import feign.Logger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class LongCatFeignConfig {

    @Value("${longcat.api.key}")  // 和 YAML 对应
    private String apiKey;

    @Bean
    public RequestInterceptor longCatAuthInterceptor() {
        return template -> {
            String authHeader = "Bearer " + apiKey;

            // 只打印是否有值，避免日志泄露完整 key
            log.info("LongCat API Key loaded? {}", apiKey != null);

            template.header("Authorization", authHeader);
            template.header("Content-Type", "application/json");
            template.header("Accept", "application/json");
            template.header("User-Agent", "Activity-Management/1.0");
        };
    }

    @Bean
    public Logger.Level feignLoggerLevel() {
        return Logger.Level.FULL;
    }
}
