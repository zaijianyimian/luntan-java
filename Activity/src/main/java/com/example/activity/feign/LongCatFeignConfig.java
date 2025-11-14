package com.example.activity.feign;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class LongCatFeignConfig {

    @Value("${logcat.api.key}")
    private String apiKey;
    @Bean
    public feign.RequestInterceptor longCatAuthInterceptor(org.springframework.core.env.Environment env) {
        return template -> {

            if (apiKey == null || apiKey.isBlank()) {
                apiKey = env.getProperty("LONGCAT_API_KEY"); // 兼容环境变量
            }
            if (apiKey != null && !apiKey.isBlank()) {
                template.header("Authorization", "Bearer " + apiKey);
            }
            template.header("Content-Type", "application/json");
            template.header("Accept", "application/json");
        };
    }
}