package com.example.activity.service;

import com.example.activity.domain.IpLocations;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Map;

/**
* @author lenovo
* @description 针对表【ip_locations(存储 IP 地址的地理位置信息)】的数据库操作Service
* @createDate 2025-11-08 13:55:21
*/
public interface IpLocationsService extends IService<IpLocations> {

    Map<String,Object> getClientIp(HttpServletRequest request);
    Map<String,Object> getGeoInfo(String ipAddress);
}
