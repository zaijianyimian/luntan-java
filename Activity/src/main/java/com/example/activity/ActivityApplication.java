package com.example.activity;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.data.elasticsearch.repository.config.EnableElasticsearchRepositories;

@SpringBootApplication
@ComponentScan(basePackages = {"com.example.activity", "com.example.filter"})
@MapperScan(basePackages = {"com.example.filter.generator.mapper","com.example.activity.mapper"})
@EnableAsync
@EnableElasticsearchRepositories(basePackages = "com.example.activity.es")
@EnableFeignClients("com.example.activity.feign")
public class ActivityApplication {
    public static void main(String[] args) {
        SpringApplication.run(ActivityApplication.class, args);
    }

}
