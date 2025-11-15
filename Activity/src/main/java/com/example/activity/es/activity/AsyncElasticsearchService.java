package com.example.activity.es.activity;

import com.example.activity.pojo.ActivityESSave;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncElasticsearchService {

    @Resource
    private ActivityES activityES;

    // 使用命名执行器，便于容量治理与监控
    @Async("asyncExecutor")
    public void indexAsync(ActivityESSave activities) {
        activityES.save(activities);
    }

    // 使用命名执行器，便于容量治理与监控
    @Async("asyncExecutor")
    public void deleteAsync(Integer id) {
        activityES.deleteById(id);
    }
}
