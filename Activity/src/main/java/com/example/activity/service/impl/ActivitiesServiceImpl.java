package com.example.activity.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.activity.domain.Activities;
import com.example.activity.service.ActivitiesService;
import com.example.activity.mapper.ActivitiesMapper;
import jakarta.annotation.Resource;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Transactional
public class ActivitiesServiceImpl extends ServiceImpl<ActivitiesMapper, Activities>
    implements ActivitiesService{

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Async
    @Override
    public void delayedCacheDelete(String key, long delayMillis) {
        try {
            Thread.sleep(delayMillis);
        } catch (InterruptedException e) {
            // 恢复中断标记并停止后续操作，避免线程资源浪费
            Thread.currentThread().interrupt();
            return;
        }
        stringRedisTemplate.delete(key);
    }

    @Override
    public void updateGeoLocation(Integer id, java.math.BigDecimal longitude, java.math.BigDecimal latitude) {
        this.baseMapper.updateLocationPoint(id, longitude, latitude);
    }

    @Override
    public Activities saveActivityAndUpdateGeo(Activities activities) {
        boolean saved = this.save(activities);
        if (!saved) return null;
        if (activities.getLongitude() != null && activities.getLatitude() != null) {
            this.baseMapper.updateLocationPoint(
                    activities.getId(),
                    java.math.BigDecimal.valueOf(activities.getLongitude()),
                    java.math.BigDecimal.valueOf(activities.getLatitude())
            );
        }
        return activities;
    }

    @Override
    public java.util.List<Activities> findNearby(double longitude, double latitude, int radiusMeters, int limit) {
        if (limit <= 0) limit = 50;
        if (radiusMeters <= 0) radiusMeters = 10_000;
        return this.baseMapper.findNearby(longitude, latitude, radiusMeters, limit);
    }
}




