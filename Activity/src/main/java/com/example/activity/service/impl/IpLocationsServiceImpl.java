package com.example.activity.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.activity.domain.IpLocations;
import com.example.activity.service.IpLocationsService;
import com.example.activity.mapper.IpLocationsMapper;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.record.City;
import com.maxmind.geoip2.record.Country;
import com.maxmind.geoip2.record.Location;
import com.maxmind.geoip2.record.Subdivision;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;

/**
 * @description 针对表【ip_locations(存储 IP 地址的地理位置信息)】的数据库操作Service实现
 * @createDate 2025-11-08 13:55:21
 */
@Service
public class IpLocationsServiceImpl extends ServiceImpl<IpLocationsMapper, IpLocations>
        implements IpLocationsService {
    @Resource
    private IpLocationsMapper ipLocationsMapper;
    private DatabaseReader databaseReader;

    // 在应用启动时加载 GeoLite2 数据库
    @PostConstruct
    public void init() throws IOException {
        File database = new File("D:/xitongyiny/dbip-city-lite-2025-11.mmdb");
        databaseReader = new DatabaseReader.Builder(database).build();
    }

    // 查询 IP 地址的地理位置信息，并将其存入数据库，返回一个 Map 类型的数据
    public Map<String, Object> getGeoInfo(String ipAddress) {
        Map<String, Object> result = new HashMap<>();
        try {
            City city = databaseReader.city(InetAddress.getByName(ipAddress)).getCity();
            Country country = databaseReader.country(InetAddress.getByName(ipAddress)).getCountry();
            Subdivision subdivision = databaseReader.city(InetAddress.getByName(ipAddress)).getMostSpecificSubdivision();
            String cityName = city.getName();
            String countryName = country.getName();
            String regionName = subdivision.getName();
            Location location = databaseReader.city(InetAddress.getByName(ipAddress)).getLocation();
            BigDecimal latitude = BigDecimal.valueOf(location.getLatitude());
            BigDecimal longitude = BigDecimal.valueOf(location.getLongitude());

            // 将查询结果存入数据库
            IpLocations ipLocations = new IpLocations(ipAddress, countryName, regionName, cityName, latitude, longitude);
            ipLocationsMapper.insertOrUpdate(ipLocations);

            // 将查询结果存储到 Map 中并返回
            result.put("country", countryName);
            result.put("region", regionName);
            result.put("city", cityName);
            result.put("latitude", latitude);
            result.put("longitude", longitude);
            result.put("ip", ipAddress);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("error", "获取地理位置信息失败");
        }
        return result;
    }

    // 获取客户端的 IP 地址，返回 Map 类型的数据
    public Map<String, Object> getClientIp(HttpServletRequest req) {
        Map<String, Object> result = new HashMap<>();
        String clientIp = req.getHeader("x-forwarded-for");
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = req.getHeader("Proxy-Client-IP");
        }
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = req.getHeader("WL-Proxy-Client-IP");
        }
        if (clientIp == null || clientIp.isEmpty() || "unknown".equalsIgnoreCase(clientIp)) {
            clientIp = req.getRemoteAddr();
        }

        // 将客户端 IP 存储在 Map 中并返回
        result.put("ip", clientIp);
        return result;
    }
}
