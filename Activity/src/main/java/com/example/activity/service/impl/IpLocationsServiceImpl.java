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
import org.springframework.beans.factory.annotation.Value;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetAddress;
import com.example.activity.dto.IpInfoDTO;
import com.example.activity.dto.GeoInfoDTO;

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
    @Value("${geoip.mmdb.path:}")
    private String mmdbPath;
    @Value("${ip2region.xdb.path:}")
    private String ip2rPath;
    @Value("${ip2region.xdb.classpath:}")
    private String ip2rClasspath;
    private org.lionsoul.ip2region.xdb.Searcher ip2Searcher;

    // 在应用启动时加载 GeoLite2 数据库
    @PostConstruct
    public void init() throws IOException {
        // 初始化 MaxMind
        if (mmdbPath != null && !mmdbPath.isBlank()) {
            File database = new File(mmdbPath);
            if (database.exists()) {
                databaseReader = new DatabaseReader.Builder(database).build();
            }
        }
        // 初始化 ip2region（优先）
        try {
            if (ip2rPath != null && !ip2rPath.isBlank()) {
                ip2Searcher = org.lionsoul.ip2region.xdb.Searcher.newWithFileOnly(ip2rPath);
            } else if (ip2rClasspath != null && !ip2rClasspath.isBlank()) {
                org.springframework.core.io.ClassPathResource res = new org.springframework.core.io.ClassPathResource(ip2rClasspath);
                if (res.exists()) {
                    byte[] buf = org.springframework.util.StreamUtils.copyToByteArray(res.getInputStream());
                    ip2Searcher = org.lionsoul.ip2region.xdb.Searcher.newWithBuffer(buf);
                }
            }
        } catch (Exception ignore) {}
    }

    // 查询 IP 地址的地理位置信息，并将其存入数据库，返回一个 Map 类型的数据
    public GeoInfoDTO getGeoInfo(String ipAddress) {
        GeoInfoDTO result = new GeoInfoDTO();
        try {
            // 优先使用 ip2region
            if (ip2Searcher != null) {
                long ipLong = ipToLong(ipAddress);
                String region = ip2Searcher.search(ipLong);
                // region 格式：国家|省份|城市|ISP
                String countryName = null, regionName = null, cityName = null;
                if (region != null) {
                    String[] parts = region.split("\\|");
                    if (parts.length >= 3) {
                        countryName = parts[0];
                        regionName = parts[1];
                        cityName = parts[2];
                    }
                }
                IpLocations ipLocations = new IpLocations(ipAddress, countryName, regionName, cityName, null, null);
                ipLocationsMapper.insertOrUpdate(ipLocations);
                result.setCountry(countryName);
                result.setRegion(regionName);
                result.setCity(cityName);
                result.setIp(ipAddress);
                return result;
            }
            // 回退 MaxMind
            if (databaseReader != null) {
                var inet = InetAddress.getByName(ipAddress);
                var cityResponse = databaseReader.city(inet);
                City city = cityResponse.getCity();
                Country country = cityResponse.getCountry();
                Subdivision subdivision = cityResponse.getMostSpecificSubdivision();
                String cityName = city.getName();
                String countryName = country.getName();
                String regionName = subdivision.getName();
                Location location = cityResponse.getLocation();
                BigDecimal latitude = BigDecimal.valueOf(location.getLatitude());
                BigDecimal longitude = BigDecimal.valueOf(location.getLongitude());
                IpLocations ipLocations = new IpLocations(ipAddress, countryName, regionName, cityName, latitude, longitude);
                ipLocationsMapper.insertOrUpdate(ipLocations);
                result.setCountry(countryName);
                result.setRegion(regionName);
                result.setCity(cityName);
                result.setLatitude(latitude);
                result.setLongitude(longitude);
                result.setIp(ipAddress);
                return result;
            }
            result.setError("GeoIP 未配置：请设置 ip2region.xdb.path 或 geoip.mmdb.path");
        } catch (Exception e) {
            result.setError("获取地理位置信息失败");
        }
        return result;
    }

    // 获取客户端的 IP 地址，返回 Map 类型的数据
    public IpInfoDTO getClientIp(HttpServletRequest req) {
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
        return new IpInfoDTO(clientIp);
    }

    private long ipToLong(String ip) {
        if (ip == null || ip.isBlank()) return 0L;
        String[] parts = ip.trim().split("\\.");
        if (parts.length != 4) return 0L;
        long res = 0;
        for (int i = 0; i < 4; i++) {
            int n;
            try { n = Integer.parseInt(parts[i]); } catch (Exception e) { n = 0; }
            res = (res << 8) | (n & 0xFF);
        }
        return res;
    }
}
