package com.example.logreg.service;

import com.example.logreg.generator.domain.SysUser;
import com.example.logreg.generator.mapper.SysUserMapper;
import jakarta.annotation.Resource;
import org.springframework.data.geo.Circle;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.Metrics;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.GeoOperations;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class NearbyService {

    private static final String GEO_KEY = "user:geo";

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private SysUserMapper sysUserMapper;

    public void updateUserLocation(Long userId, double latitude, double longitude) {
        // 使用管道一次性写入 GEO 与时间戳 TTL
        stringRedisTemplate.executePipelined(new org.springframework.data.redis.core.SessionCallback<Object>() {
            @Override
            public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                GeoOperations<String, String> geo = operations.opsForGeo();
                geo.add(GEO_KEY, new Point(longitude, latitude), String.valueOf(userId));
                operations.opsForValue().set("user:geo:ts:" + userId, String.valueOf(System.currentTimeMillis()), java.time.Duration.ofHours(24));
                return null;
            }
        });
    }

    public java.util.List<Map<String, Object>> findNearby(double latitude, double longitude, int radiusKm, int limit, Long selfUserId) {
        GeoOperations<String, String> geo = stringRedisTemplate.opsForGeo();
        Circle circle = new Circle(new Point(longitude, latitude), new Distance(radiusKm, Metrics.KILOMETERS));
        var args = RedisGeoCommands.GeoRadiusCommandArgs.newGeoRadiusArgs().includeDistance().sortAscending().limit(limit);
        var results = geo.radius(GEO_KEY, circle, args);
        if (results == null || results.getContent().isEmpty()) {
            return java.util.Collections.emptyList();
        }
        java.util.List<String> ids = new java.util.ArrayList<>();
        java.util.Map<String, Double> distMap = new java.util.HashMap<>();
        for (var r : results.getContent()) {
            String mid = r.getContent().getName();
            if (mid == null) continue;
            if (selfUserId != null && mid.equals(String.valueOf(selfUserId))) continue;
            ids.add(mid);
            if (r.getDistance() != null) distMap.put(mid, r.getDistance().getValue()); // 已是公里单位
        }
        if (ids.isEmpty()) return java.util.Collections.emptyList();
        // 过期过滤：仅保留 24h 内上报过位置的用户
        java.util.List<String> tsKeys = ids.stream().map(id -> "user:geo:ts:" + id).collect(Collectors.toList());
        java.util.List<String> tsVals = stringRedisTemplate.opsForValue().multiGet(tsKeys);
        java.util.Set<String> validIds = new java.util.HashSet<>();
        for (int i = 0; i < ids.size(); i++) {
            String v = tsVals != null && i < tsVals.size() ? tsVals.get(i) : null;
            if (v != null) validIds.add(ids.get(i));
        }
        if (validIds.isEmpty()) return java.util.Collections.emptyList();
        java.util.List<Long> idLongs = ids.stream().map(Long::valueOf).collect(Collectors.toList());
        java.util.List<SysUser> users = sysUserMapper.selectBatchIds(idLongs);
        if (users == null) users = java.util.Collections.emptyList();
        java.util.List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (SysUser u : users) {
            if (u.getDeleted() != null && u.getDeleted() == 1) continue;
            if (!validIds.contains(String.valueOf(u.getId()))) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("userId", u.getId());
            m.put("username", u.getUsername());
            m.put("nickname", u.getNickname());
            Double dkm = distMap.get(String.valueOf(u.getId()));
            m.put("distanceKm", dkm == null ? null : Math.round(dkm * 10.0) / 10.0);
            out.add(m);
        }
        out.sort(java.util.Comparator.comparingDouble(o -> {
            Object v = o.get("distanceKm");
            return v == null ? Double.MAX_VALUE : ((Double) v);
        }));
        if (out.size() > limit) return out.subList(0, limit);
        return out;
    }

    public java.util.List<Map<String, Object>> findNearbyByMember(Long userId, int radiusKm, int limit) {
        java.util.List<Point> pos = stringRedisTemplate.opsForGeo().position(GEO_KEY, String.valueOf(userId));
        if (pos == null || pos.isEmpty() || pos.get(0) == null) return java.util.Collections.emptyList();
        Point p = pos.get(0);
        return findNearby(p.getY(), p.getX(), radiusKm, limit, userId);
    }
}