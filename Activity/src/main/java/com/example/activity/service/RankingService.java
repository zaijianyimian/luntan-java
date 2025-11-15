package com.example.activity.service;

import com.example.activity.dto.ActivityRankDTO;
import jakarta.annotation.Resource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class RankingService {
    private static final String PREFIX = "rank:activities:";
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private String keyForToday() {
        LocalDate d = LocalDate.now();
        return PREFIX + d.toString().replace("-", "");
    }

    public void incLikeScore(Integer activityId) {
        String key = keyForToday();
        stringRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(activityId), 1.0);
        ensureExpire(key);
    }

    public void incCommentScore(Integer activityId) {
        String key = keyForToday();
        stringRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(activityId), 2.0);
        ensureExpire(key);
    }

    public void decLikeScore(Integer activityId) {
        String key = keyForToday();
        stringRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(activityId), -1.0);
        ensureExpire(key);
    }

    public void decCommentScore(Integer activityId) {
        String key = keyForToday();
        stringRedisTemplate.opsForZSet().incrementScore(key, String.valueOf(activityId), -2.0);
        ensureExpire(key);
    }

    public void removeActivity(Integer activityId) {
        String key = keyForToday();
        stringRedisTemplate.opsForZSet().remove(key, String.valueOf(activityId));
    }

    public List<ActivityRankDTO> topN(int n) {
        String key = keyForToday();
        var tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(key, 0, n - 1);
        List<ActivityRankDTO> out = new ArrayList<>();
        if (tuples == null) return out;
        for (var t : tuples) {
            ActivityRankDTO dto = new ActivityRankDTO();
            dto.setActivityId(Integer.valueOf(t.getValue()));
            dto.setScore(t.getScore());
            try {
                var h = stringRedisTemplate.opsForHash().entries("activity:" + t.getValue());
                if (h != null) dto.setTitle((String) h.get("title"));
            } catch (Exception ignore) {}
            out.add(dto);
        }
        return out;
    }

    private void ensureExpire(String key) {
        Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
        if (ttl == null || ttl == -1L) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay();
            long seconds = Duration.between(now, nextMidnight).getSeconds();
            if (seconds <= 0) seconds = TimeUnit.HOURS.toSeconds(24);
            stringRedisTemplate.expire(key, seconds, TimeUnit.SECONDS);
        }
    }
}
