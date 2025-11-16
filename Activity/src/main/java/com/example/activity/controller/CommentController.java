package com.example.activity.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.activity.domain.Comments;
import com.example.activity.dto.FailureDTO;
import com.example.activity.dto.MessageDTO;
import com.example.activity.dto.SuccessDTO;
import com.example.activity.es.comments.CommentsIndexService;
import com.example.activity.feign.UserServiceClient;
import com.example.activity.message.Messages;
import com.example.activity.pojo.CommentESSave;
import com.example.activity.service.CommentsService;
import com.example.activity.vo.CommentAddVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.web.bind.annotation.*;
import com.example.activity.feign.UserServiceClient;
import com.example.activity.dto.UserBasicDTO;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@RestController
@RequestMapping("/api")
@Tag(name = "评论管理")
public class CommentController {

    private static final String COMMENT_HASH_KEY_PREFIX = "comment:"; // Hash: comment:{id}
    private static final long COMMENT_TTL_MINUTES = 10L;

    @Resource
    private CommentsService commentsService;

    @Resource
    private CommentsIndexService commentsIndexService;

    @jakarta.annotation.Resource
    private com.example.activity.service.RankingService rankingService;

    /** 关键：使用 StringRedisTemplate，避免把 Hash 的值序列化成 JSON 导致 HINCRBY 报错 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserServiceClient userServiceClient;

    @Resource
    private RabbitTemplate rabbitTemplate;

    // ----------------- 工具方法 -----------------

    private String keyOf(Integer id) {
        return COMMENT_HASH_KEY_PREFIX + id;
    }

    /** 将 DB/ES 实体写入 Redis Hash，并设置 TTL=10min；countLikes 一律写为纯数字字符串 */
    private void cacheCommentToRedis(Comments c) {
        String key = keyOf(c.getId());
        HashOperations<String, Object, Object> h = stringRedisTemplate.opsForHash();
        h.put(key, "id", String.valueOf(c.getId()));
        h.put(key, "userId", String.valueOf(c.getUserId()));
        h.put(key, "activityId", String.valueOf(c.getActivityId()));
        h.put(key, "description", c.getDescription());
        h.put(key, "countLikes", String.valueOf(c.getCountLikes() == null ? 0L : c.getCountLikes()));
        stringRedisTemplate.expire(key, COMMENT_TTL_MINUTES, TimeUnit.MINUTES);
    }

    

    /** 初始化 Redis：优先从 ES 取；ES 没有再回退 DB；最后确保 countLikes 是数字 */
    private Comments initRedisFromEsOrDb(Integer id) {
        // 先 ES
        CommentESSave esDoc = null;
        try {
            esDoc = commentsIndexService.findById(id);
        } catch (Exception ignore) {
        }

        if (esDoc != null) {
            Comments c = new Comments();
            c.setId(esDoc.getId());
            c.setUserId(esDoc.getUserId());
            c.setActivityId(esDoc.getActivityId());
            c.setDescription(esDoc.getDescription());
            c.setCountLikes(esDoc.getCountLikes() == null ? 0L : esDoc.getCountLikes());
            cacheCommentToRedis(c);
            return c;
        }

        // 回退 DB
        Comments db = commentsService.getById(id);
        if (db != null) {
            if (db.getCountLikes() == null) db.setCountLikes(0L);
            cacheCommentToRedis(db);
        }
        return db;
    }

    // ----------------- 1) 新增评论：DB -> ES -> Redis(Hash, 10min) -----------------

    @PostMapping("/activity/comment")
    public MessageDTO addComment(@RequestBody CommentAddVO vo) {
        if (vo == null
                || vo.getActivityId() == null
                || vo.getDescription() == null
                || vo.getDescription().isBlank()) {
            return new FailureDTO<>(Messages.BAD_REQUEST, null);
        }

        Long uid = currentUserId();
        if (uid == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, null);
        }

        Comments comment = new Comments();
        comment.setUserId(uid);
        comment.setActivityId(vo.getActivityId());
        comment.setDescription(vo.getDescription().trim());
        comment.setCountLikes(0L);

        boolean saved = commentsService.save(comment);
        if (!saved || comment.getId() == null) {
            return new FailureDTO<>(Messages.SERVER_ERROR, null);
        }

        if (comment.getActivityId() != null) {
            rankingService.incCommentScore(comment.getActivityId());
        }

        // ES 索引（upsert）
        commentsIndexService.indexAfterCommit(new CommentESSave(comment));

        // Redis Hash（10 分钟）
        cacheCommentToRedis(comment);

        return new SuccessDTO<>(comment.getId());
    }

    // ----------------- 2) 删除：先删 Redis -> 再删 DB -> 再删 ES -> 再尝试删 Redis -----------------

    @DeleteMapping("/activity/comment/{id}")
    public MessageDTO deleteComment(@PathVariable Integer id) {
        String hashKey = keyOf(id);

        // 先删 Redis（容错：不存在也无所谓）
        stringRedisTemplate.delete(hashKey);

        // 删 DB
        Comments exist = commentsService.getById(id);
        if (exist == null) {
            return new FailureDTO<>(Messages.NOT_FOUND, id);
        }
        boolean removed = commentsService.removeById(id);
        if (!removed) {
            return new FailureDTO<>(Messages.SERVER_ERROR, id);
        }
        try {
            if (exist.getActivityId() != null) {
                rankingService.decCommentScore(exist.getActivityId());
            }
        } catch (Exception ignore) {}

        // 删 ES
        try {
            commentsIndexService.deleteAfterCommit(id);
        } catch (Exception e) {
            log.warn("删除 ES 失败，但 DB 已删除，id={}", id, e);
        }

        // 再次尝试删 Redis，保证幂等
        stringRedisTemplate.delete(hashKey);

        return new SuccessDTO<>(id);
    }

    // ----------------- 3) 点赞：Redis -> （缺失则 ES/DB 回填）-> HINCRBY +1 -> TTL -> 同步 DB & ES -----------------

    @GetMapping("/activity/comment/like/{id}")
    public MessageDTO likeComment(@PathVariable Integer id) {
        String key = keyOf(id);
        HashOperations<String, Object, Object> h = stringRedisTemplate.opsForHash();
        Long userId = currentUserId();
        if (userId == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, id);
        }
        String ukey = "comment:liked:users:" + id;
        Object v = h.get(key, "countLikes");
        String baseStr;
        try { baseStr = String.valueOf(Long.parseLong(String.valueOf(v))); } catch (Exception e) {
            Comments base = initRedisFromEsOrDb(id);
            if (base == null) return new FailureDTO<>(Messages.NOT_FOUND, id);
            baseStr = String.valueOf(base.getCountLikes() == null ? 0L : base.getCountLikes());
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("local v=redis.call('HGET', KEYS[1], 'countLikes'); if redis.call('SISMEMBER', KEYS[2], ARGV[2])==1 then return -1 end; if not v or tonumber(v)==nil then redis.call('HSET', KEYS[1], 'countLikes', ARGV[1]) end; local new=redis.call('HINCRBY', KEYS[1], 'countLikes', 1); redis.call('EXPIRE', KEYS[1], ARGV[3]); redis.call('SADD', KEYS[2], ARGV[2]); redis.call('EXPIRE', KEYS[2], ARGV[4]); return new");
        script.setResultType(Long.class);
        Long res = stringRedisTemplate.execute(script, java.util.Arrays.asList(key, ukey), baseStr, String.valueOf(userId), String.valueOf(600), String.valueOf(86400));
        if (res == null) return new FailureDTO<>(Messages.SERVER_ERROR, id);
        if (res == -1L) return new FailureDTO<>(Messages.BAD_REQUEST, id);
        stringRedisTemplate.opsForSet().add("changed:comments", String.valueOf(id));
        try {
            Object aidObj = h.get(key, "activityId");
            Integer aid = aidObj == null ? null : Integer.parseInt(String.valueOf(aidObj));
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("commentId", id);
            if (aid != null) item.put("activityId", aid);
            item.put("countLikes", res);
            java.util.List<Long> users = new java.util.ArrayList<>();
            users.add(userId);
            item.put("users", users);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("batchId", java.util.UUID.randomUUID().toString());
            java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
            items.add(item);
            payload.put("items", items);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            rabbitTemplate.convertAndSend(com.example.activity.config.RabbitMQConfig.EXCHANGE_NAME, com.example.activity.config.RabbitMQConfig.ROUTING_COMMENT, json);
        } catch (Exception ignore) {}
        return new SuccessDTO<>(res);
    }

    // ----------------- 5) 取消点赞：同点赞，但 HINCRBY -1（不小于 0） -----------------

    @GetMapping("/activity/comment/unlike/{id}")
    public MessageDTO unlikeComment(@PathVariable Integer id) {
        String key = keyOf(id);
        HashOperations<String, Object, Object> h = stringRedisTemplate.opsForHash();
        Long userId = currentUserId();
        if (userId == null) {
            return new FailureDTO<>(Messages.BAD_REQUEST, id);
        }
        String ukey = "comment:liked:users:" + id;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText("if redis.call('SISMEMBER', KEYS[2], ARGV[1])==0 then return -2 end; local v=redis.call('HGET', KEYS[1], 'countLikes'); if not v or tonumber(v)==nil then redis.call('HSET', KEYS[1], 'countLikes', '0'); v='0' end; local nv=tonumber(v); if not nv or nv<=0 then redis.call('HSET', KEYS[1], 'countLikes', '0'); redis.call('SREM', KEYS[2], ARGV[1]); redis.call('EXPIRE', KEYS[1], ARGV[2]); redis.call('EXPIRE', KEYS[2], ARGV[3]); return 0 end; local new=redis.call('HINCRBY', KEYS[1], 'countLikes', -1); redis.call('SREM', KEYS[2], ARGV[1]); redis.call('EXPIRE', KEYS[1], ARGV[2]); redis.call('EXPIRE', KEYS[2], ARGV[3]); return new");
        script.setResultType(Long.class);
        Long res = stringRedisTemplate.execute(script, java.util.Arrays.asList(key, ukey), String.valueOf(userId), String.valueOf(600), String.valueOf(86400));
        if (res == null) return new FailureDTO<>(Messages.SERVER_ERROR, id);
        if (res == -2L) return new FailureDTO<>(Messages.BAD_REQUEST, id);
        stringRedisTemplate.opsForSet().add("changed:comments", String.valueOf(id));
        try {
            Object aidObj = h.get(key, "activityId");
            Integer aid = aidObj == null ? null : Integer.parseInt(String.valueOf(aidObj));
            java.util.Map<String, Object> item = new java.util.HashMap<>();
            item.put("commentId", id);
            if (aid != null) item.put("activityId", aid);
            item.put("countLikes", res);
            java.util.List<Long> unlikes = new java.util.ArrayList<>();
            unlikes.add(userId);
            item.put("unlikes", unlikes);
            java.util.Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("batchId", java.util.UUID.randomUUID().toString());
            java.util.List<java.util.Map<String, Object>> items = new java.util.ArrayList<>();
            items.add(item);
            payload.put("items", items);
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payload);
            rabbitTemplate.convertAndSend(com.example.activity.config.RabbitMQConfig.EXCHANGE_NAME, com.example.activity.config.RabbitMQConfig.ROUTING_COMMENT, json);
        } catch (Exception ignore) {}
        return new SuccessDTO<>(res);
    }

    private Long currentUserId() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) return null;
            String uidHeader = attrs.getRequest().getHeader("X-User-Id");
            if (uidHeader != null && !uidHeader.isBlank()) {
                try { return Long.valueOf(uidHeader); } catch (Exception ignore) {}
            }
            String auth = attrs.getRequest().getHeader("Authorization");
            if (auth == null || !auth.startsWith("Bearer ")) return null;
            String payload = new String(java.util.Base64.getUrlDecoder().decode(auth.substring(7).split("\\.")[1]), java.nio.charset.StandardCharsets.UTF_8);
            var tree = new ObjectMapper().readTree(payload);
            if (tree.has("sub")) return tree.get("sub").asLong();
            if (tree.has("userId")) return tree.get("userId").asLong();
            return null;
        } catch (Exception e) { return null; }
    }

    // ----------------- 4) 根据 activityId 获取（保持不变） -----------------

    @GetMapping("/activity/comment/activityid/{id}")
    public MessageDTO getCommentsByActivityId(@PathVariable Integer id) {
        List<CommentESSave> commentESSaves = commentsIndexService.findByActivityId(id);
        if (commentESSaves != null) {
            java.util.Set<Long> uids = commentESSaves.stream().map(CommentESSave::getUserId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
            if (!uids.isEmpty()) {
                var resp = userServiceClient.basics(uids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
                java.util.Map<Long, String> m = new java.util.HashMap<>();
                if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                    java.util.List<UserBasicDTO> ul = resp.getData()[0];
                    if (ul != null) {
                        for (UserBasicDTO u : ul) {
                            if (u.getUserId() != null) m.put(u.getUserId(), u.getAvatarUrl());
                        }
                    }
                }
                for (var c : commentESSaves) {
                    if (c.getUserId() != null) c.setUserImage(m.get(c.getUserId()));
                }
            }
            return new SuccessDTO<>(commentESSaves);
        } else {
            QueryWrapper<Comments> qr = new QueryWrapper<>();
            qr.eq("activity_id", id);
            List<Comments> list = commentsService.list(qr);
            if (list != null) {
                java.util.List<CommentESSave> out = new java.util.ArrayList<>();
                java.util.Set<Long> uids = list.stream().map(Comments::getUserId).filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
                java.util.Map<Long, String> m = new java.util.HashMap<>();
                if (!uids.isEmpty()) {
                    var resp = userServiceClient.basics(uids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(",")));
                    if (resp != null && resp.getData() != null && resp.getData().length > 0) {
                        java.util.List<UserBasicDTO> ul = resp.getData()[0];
                        if (ul != null) {
                            for (UserBasicDTO u : ul) {
                                if (u.getUserId() != null) m.put(u.getUserId(), u.getAvatarUrl());
                            }
                        }
                    }
                }
                for (Comments c : list) {
                    CommentESSave ce = new CommentESSave(c);
                    ce.setUserImage(m.get(c.getUserId()));
                    out.add(ce);
                }
                return new SuccessDTO<>(out);
            } else {
                return new FailureDTO<>(Messages.NOT_FOUND, null);
            }
        }
    }
}
