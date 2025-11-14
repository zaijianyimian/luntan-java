package com.example.activity;

import com.example.activity.es.activity.ActivityES;
import jakarta.annotation.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ActivityApplicationTests {
    @Resource
    private ActivityES activityES;
    @Test
    void contextLoads() {
    }



}
