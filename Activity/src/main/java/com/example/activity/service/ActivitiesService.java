package com.example.activity.service;

import com.example.activity.domain.Activities;
import com.baomidou.mybatisplus.extension.service.IService;

import java.math.BigDecimal;

/**
* @author lenovo
* @description 针对表【activities】的数据库操作Service
* @createDate 2025-11-08 13:55:41
*/
public interface ActivitiesService extends IService<Activities> {
    void delayedCacheDelete(String key, long delayMillis);
    void updateGeoLocation(Integer id, BigDecimal longitude, BigDecimal latitude);
    /**
     * 在同一事务中保存活动并（在有经纬度时）更新几何位置
     */
    Activities saveActivityAndUpdateGeo(Activities activities);

    /**
     * 查找附近范围内的活动（默认半径单位：米）
     */
    java.util.List<Activities> findNearby(double longitude, double latitude, int radiusMeters, int limit);
}
