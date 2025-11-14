package com.example.likes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;

@SpringBootApplication
@MapperScan("com.example.likes.mapper")
@EnableScheduling
@EnableRabbit
public class LikesApplication {

    public static void main(String[] args) {
        SpringApplication.run(LikesApplication.class, args);
    }

}
