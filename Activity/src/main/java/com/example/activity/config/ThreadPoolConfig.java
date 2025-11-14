package com.example.activity.config;

import lombok.Data;
import lombok.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Configuration
public class ThreadPoolConfig {
    @Bean
    public ExecutorService getExecutorService(){
        return new ThreadPoolExecutor(20,  500, 3600L,
                TimeUnit.SECONDS, new ArrayBlockingQueue<>(100));
    }
}
