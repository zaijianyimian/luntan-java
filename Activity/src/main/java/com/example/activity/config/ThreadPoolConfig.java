package com.example.activity.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionHandler;

@Configuration
public class ThreadPoolConfig {
    /**
     * 命名的 @Async 执行器，专用于异步任务（ES、缓存等）。
     * 使用 CallerRunsPolicy 作为拒绝策略，避免静默丢任务，并便于回压。
     */
    @Bean("asyncExecutor")
    public ThreadPoolTaskExecutor asyncExecutor() {
        ThreadPoolTaskExecutor t = new ThreadPoolTaskExecutor();
        t.setCorePoolSize(8);
        t.setMaxPoolSize(32);
        t.setQueueCapacity(1000);
        t.setKeepAliveSeconds(60);
        t.setThreadNamePrefix("async-exec-");
        t.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        t.initialize();
        return t;
    }

    /**
     * 备用业务线程池，供需要 ExecutorService 的场景使用。
     */
    @Bean
    public ExecutorService getExecutorService(){
        return new ThreadPoolExecutor(
                8,
                64,
                60L,
                TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(500),
                new ThreadFactory() {
                    private final ThreadFactory df = Executors.defaultThreadFactory();
                    @Override
                    public Thread newThread(Runnable r) {
                        Thread t = df.newThread(r);
                        t.setName("biz-exec-" + t.getId());
                        return t;
                    }
                },
                new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
