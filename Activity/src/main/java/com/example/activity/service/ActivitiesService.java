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
}
