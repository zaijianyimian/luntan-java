package com.example.activity.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class LikeBatchPublisher {

    private final StringRedisTemplate stringRedisTemplate;
    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper mapper = new ObjectMapper();

    public LikeBatchPublisher(StringRedisTemplate stringRedisTemplate, RabbitTemplate rabbitTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.rabbitTemplate = rabbitTemplate;
    }

    private static final String LAST_COMMENTS_TS_KEY = "publish:last:comments";
    private static final String LAST_ACTIVITIES_TS_KEY = "publish:last:activities";
    private static final long WINDOW_MS = 30_000L;
    private static final long BATCH_THRESHOLD = 1000L;
    private static final int BATCH_LIMIT = 1000;

    @Scheduled(fixedDelay = 30000, initialDelay = 30000)
    public void publish() throws Exception {
        publishComments();
        publishActivities();
    }

    private void publishComments() throws Exception {
        Long size = stringRedisTemplate.opsForSet().size("changed:comments");
        long now = System.currentTimeMillis();
        Long last = null;
        try {
            String s = stringRedisTemplate.opsForValue().get(LAST_COMMENTS_TS_KEY);
            if (s != null) last = Long.parseLong(s);
        } catch (Exception ignore) {}
        boolean thresholdHit = size != null && size >= BATCH_THRESHOLD;
        boolean windowHit = last == null || now - last >= WINDOW_MS;
        if (!(thresholdHit || windowHit)) return;

        Set<String> all = stringRedisTemplate.opsForSet().members("changed:comments");
        if (all == null || all.isEmpty()) return;
        java.util.List<String> ids = new java.util.ArrayList<>(all);
        if (ids.size() > BATCH_LIMIT) ids = ids.subList(0, BATCH_LIMIT);
        HashOperations<String, Object, Object> h = stringRedisTemplate.opsForHash();
        List<Map<String, Object>> items = new ArrayList<>();
        for (String sid : ids) {
            String key = "comment:" + sid;
            Object aid = h.get(key, "activityId");
            Object v = h.get(key, "countLikes");
            if (v == null) v = "0";
            long like;
            try { like = Long.parseLong(String.valueOf(v)); } catch (Exception e) { like = 0L; }
            Map<String, Object> m = new HashMap<>();
            m.put("commentId", Long.valueOf(sid));
            m.put("activityId", aid == null ? 0 : Integer.parseInt(String.valueOf(aid)));
            m.put("countLikes", like);
            m.put("ts", System.currentTimeMillis());
            Set<String> users = stringRedisTemplate.opsForSet().members("comment:liked:users:" + sid);
            if (users != null && !users.isEmpty()) {
                m.put("users", new ArrayList<>(users));
            }
            items.add(m);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchId", UUID.randomUUID().toString());
        payload.put("items", items);
        String json = mapper.writeValueAsString(payload);
        rabbitTemplate.convertAndSend("luntanexchange", "comment", json);
        stringRedisTemplate.opsForSet().remove("changed:comments", ids.toArray());
        stringRedisTemplate.opsForValue().set(LAST_COMMENTS_TS_KEY, String.valueOf(now));
    }

    private void publishActivities() throws Exception {
        Long size = stringRedisTemplate.opsForSet().size("changed:activities");
        long now = System.currentTimeMillis();
        Long last = null;
        try {
            String s = stringRedisTemplate.opsForValue().get(LAST_ACTIVITIES_TS_KEY);
            if (s != null) last = Long.parseLong(s);
        } catch (Exception ignore) {}
        boolean thresholdHit = size != null && size >= BATCH_THRESHOLD;
        boolean windowHit = last == null || now - last >= WINDOW_MS;
        if (!(thresholdHit || windowHit)) return;

        Set<String> all = stringRedisTemplate.opsForSet().members("changed:activities");
        if (all == null || all.isEmpty()) return;
        java.util.List<String> ids = new java.util.ArrayList<>(all);
        if (ids.size() > BATCH_LIMIT) ids = ids.subList(0, BATCH_LIMIT);
        HashOperations<String, Object, Object> h = stringRedisTemplate.opsForHash();
        List<Map<String, Object>> items = new ArrayList<>();
        for (String sid : ids) {
            String key = "activity:" + sid;
            Object v = h.get(key, "countLikes");
            if (v == null) v = "0";
            long like;
            try { like = Long.parseLong(String.valueOf(v)); } catch (Exception e) { like = 0L; }
            Map<String, Object> m = new HashMap<>();
            m.put("activityId", Integer.parseInt(sid));
            m.put("countLikes", like);
            m.put("ts", System.currentTimeMillis());
            try {
                java.util.Set<String> users = stringRedisTemplate.opsForSet().members("activity:liked:users:" + sid);
                if (users != null && !users.isEmpty()) {
                    m.put("users", new ArrayList<>(users));
                }
            } catch (Exception ignore) {}
            items.add(m);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchId", UUID.randomUUID().toString());
        payload.put("items", items);
        String json = mapper.writeValueAsString(payload);
        rabbitTemplate.convertAndSend("luntanexchange", "activity", json);
        stringRedisTemplate.opsForSet().remove("changed:activities", ids.toArray());
        stringRedisTemplate.opsForValue().set(LAST_ACTIVITIES_TS_KEY, String.valueOf(now));
    }
}
