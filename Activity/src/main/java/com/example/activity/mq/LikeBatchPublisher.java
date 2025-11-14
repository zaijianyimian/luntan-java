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

    @Scheduled(fixedDelay = 30000)
    public void publish() throws Exception {
        publishComments();
        publishActivities();
    }

    private void publishComments() throws Exception {
        Set<String> ids = stringRedisTemplate.opsForSet().members("changed:comments");
        if (ids == null || ids.isEmpty()) return;
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
    }

    private void publishActivities() throws Exception {
        Set<String> ids = stringRedisTemplate.opsForSet().members("changed:activities");
        if (ids == null || ids.isEmpty()) return;
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
            items.add(m);
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("batchId", UUID.randomUUID().toString());
        payload.put("items", items);
        String json = mapper.writeValueAsString(payload);
        rabbitTemplate.convertAndSend("luntanexchange", "activity", json);
        stringRedisTemplate.opsForSet().remove("changed:activities", ids.toArray());
    }
}
