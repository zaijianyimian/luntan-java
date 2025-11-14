package com.example.activity.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.activity.domain.Activities;
import com.example.activity.service.ActivitiesService;
import com.example.activity.mapper.ActivitiesMapper;
import jakarta.annotation.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class ActivitiesServiceImpl extends ServiceImpl<ActivitiesMapper, Activities>
    implements ActivitiesService{

    @Resource
    private RedisTemplate redisTemplate;

    @Async
    @Override
    public void delayedCacheDelete(String key, long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException ignored) { }
        redisTemplate.delete(key);
    }

    @Override
    public void updateGeoLocation(Integer id, java.math.BigDecimal longitude, java.math.BigDecimal latitude) {
        this.baseMapper.updateLocationPoint(id, longitude, latitude);
    }
}




