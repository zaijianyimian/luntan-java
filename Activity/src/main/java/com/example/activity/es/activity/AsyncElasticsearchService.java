package com.example.activity.es.activity;

import com.example.activity.pojo.ActivityESSave;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AsyncElasticsearchService {

    @Resource
    private ActivityES activityES;

    @Async
    public void indexAsync(ActivityESSave activities) {
        activityES.save(activities);
    }

    @Async
    public void deleteAsync(Integer id) {
        activityES.deleteById(id);
    }
}
