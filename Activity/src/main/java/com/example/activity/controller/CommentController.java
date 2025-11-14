package com.example.activity.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.activity.domain.Comments;
import com.example.activity.dto.FailureDTO;
import com.example.activity.dto.MessageDTO;
import com.example.activity.dto.SuccessDTO;
import com.example.activity.es.comments.CommentsIndexService;
import com.example.activity.message.Messages;
import com.example.activity.pojo.CommentESSave;
import com.example.activity.service.CommentsService;
import com.example.activity.vo.CommentAddVO;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

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

    /** 关键：使用 StringRedisTemplate，避免把 Hash 的值序列化成 JSON 导致 HINCRBY 报错 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

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

    /** 仅刷新 TTL */
    private void refreshTTL(String key) {
        stringRedisTemplate.expire(key, COMMENT_TTL_MINUTES, TimeUnit.MINUTES);
    }

    /** 从 Redis 读点赞数，读不到或非数字返回 null */
    private Long readLikeFromRedis(String key) {
        Object v = stringRedisTemplate.opsForHash().get(key, "countLikes");
        if (v == null) return null;
        try {
            return Long.parseLong(v.toString());
        } catch (Exception e) {
            return null;
        }
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
    @Transactional
    public MessageDTO addComment(@RequestBody CommentAddVO vo) {
        if (vo == null || vo.getUserId() == null
                || vo.getActivityId() == null
                || vo.getDescription() == null
                || vo.getDescription().isBlank()) {
            return new FailureDTO<>(Messages.BAD_REQUEST, null);
        }

        Comments comment = new Comments();
        comment.setUserId(vo.getUserId());
        comment.setActivityId(vo.getActivityId());
        comment.setDescription(vo.getDescription().trim());
        comment.setCountLikes(0L);

        boolean saved = commentsService.save(comment);
        if (!saved || comment.getId() == null) {
            return new FailureDTO<>(Messages.SERVER_ERROR, null);
        }

        // ES 索引（upsert）
        commentsIndexService.indexAfterCommit(new CommentESSave(comment));

        // Redis Hash（10 分钟）
        cacheCommentToRedis(comment);

        return new SuccessDTO<>(comment.getId());
    }

    // ----------------- 2) 删除：先删 Redis -> 再删 DB -> 再删 ES -> 再尝试删 Redis -----------------

    @DeleteMapping("/activity/comment/{id}")
    @Transactional
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
    @Transactional
    public MessageDTO likeComment(@PathVariable Integer id) {
        String key = keyOf(id);
        HashOperations<String, Object, Object> h = stringRedisTemplate.opsForHash();

        // 缓存有且是数字：直接 incr
        Long like = readLikeFromRedis(key);
        Comments base = null;
        if (like == null) {
            // 初始化缓存（优先 ES，失败回退 DB）
            base = initRedisFromEsOrDb(id);
            if (base == null) {
                return new FailureDTO<>(Messages.NOT_FOUND, id);
            }
        }

        // 兜底：保证字段是数字
        if (readLikeFromRedis(key) == null) {
            h.put(key, "countLikes", "0");
        }

        // HINCRBY +1
        Long newLike = h.increment(key, "countLikes", 1L);
        if (newLike == null) {
            return new FailureDTO<>(Messages.SERVER_ERROR, id);
        }
        refreshTTL(key);

        // 同步 DB
        Comments toUpdate = new Comments();
        toUpdate.setId(id);
        toUpdate.setCountLikes(newLike);
        commentsService.updateById(toUpdate);

        // 同步 ES（upsert）
        Comments toIndex = (base != null ? base : commentsService.getById(id));
        if (toIndex != null) {
            toIndex.setCountLikes(newLike);
            commentsIndexService.indexAfterCommit(new CommentESSave(toIndex));
        }

        return new SuccessDTO<>(newLike);
    }

    // ----------------- 5) 取消点赞：同点赞，但 HINCRBY -1（不小于 0） -----------------

    @GetMapping("/activity/comment/unlike/{id}")
    @Transactional
    public MessageDTO unlikeComment(@PathVariable Integer id) {
        String key = keyOf(id);
        HashOperations<String, Object, Object> h = stringRedisTemplate.opsForHash();

        // 缓存缺失则初始化（优先 ES，失败回退 DB）
        Long like = readLikeFromRedis(key);
        Comments base = null;
        if (like == null) {
            base = initRedisFromEsOrDb(id);
            if (base == null) {
                return new FailureDTO<>(Messages.NOT_FOUND, id);
            }
            like = readLikeFromRedis(key);
            if (like == null) like = 0L;
        }

        // 不让变成负数
        if (like <= 0) {
            h.put(key, "countLikes", "0");
            refreshTTL(key);

            // 同步 DB & ES（0）
            Comments toZero = new Comments();
            toZero.setId(id);
            toZero.setCountLikes(0L);
            commentsService.updateById(toZero);

            Comments toIndex = (base != null ? base : commentsService.getById(id));
            if (toIndex != null) {
                toIndex.setCountLikes(0L);
                commentsIndexService.indexAfterCommit(new CommentESSave(toIndex));
            }
            return new SuccessDTO<>(0L);
        }

        // HINCRBY -1
        Long newLike = h.increment(key, "countLikes", -1L);
        if (newLike == null) {
            return new FailureDTO<>(Messages.SERVER_ERROR, id);
        }
        if (newLike < 0) { // 并发极端兜底
            h.put(key, "countLikes", "0");
            newLike = 0L;
        }
        refreshTTL(key);

        // 同步 DB
        Comments toUpdate = new Comments();
        toUpdate.setId(id);
        toUpdate.setCountLikes(newLike);
        commentsService.updateById(toUpdate);

        // 同步 ES（upsert）
        Comments toIndex = (base != null ? base : commentsService.getById(id));
        if (toIndex != null) {
            toIndex.setCountLikes(newLike);
            commentsIndexService.indexAfterCommit(new CommentESSave(toIndex));
        }

        return new SuccessDTO<>(newLike);
    }

    // ----------------- 4) 根据 activityId 获取（保持不变） -----------------

    @GetMapping("/activity/comment/activityid/{id}")
    public MessageDTO getCommentsByActivityId(@PathVariable Integer id) {
        List<CommentESSave> commentESSaves = commentsIndexService.findByActivityId(id);
        if (commentESSaves != null) {
            return new SuccessDTO<>(commentESSaves);
        } else {
            QueryWrapper<Comments> qr = new QueryWrapper<>();
            qr.eq("activity_id", id);
            List<Comments> list = commentsService.list(qr);
            if (list != null) {
                return new SuccessDTO<>(list);
            } else {
                return new FailureDTO<>(Messages.NOT_FOUND, null);
            }
        }
    }
}
