package com.example.likes.mq;

import com.example.likes.domain.CommentCountLike;
import com.example.likes.domain.ActivityCountLike;
import com.example.likes.service.CommentCountLikeService;
import com.example.likes.service.ActivityCountLikeService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class LikeBatchConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    // 使用 AtomicReference 包装聚合缓存，便于在刷盘时原子交换快照，避免与消费者并发冲突
    private final AtomicReference<ConcurrentHashMap<Long, CommentCountLike>> commentAggRef = new AtomicReference<>(new ConcurrentHashMap<>());
    private final AtomicReference<ConcurrentHashMap<Integer, ActivityCountLike>> activityAggRef = new AtomicReference<>(new ConcurrentHashMap<>());
    private final AtomicReference<ConcurrentHashMap<String, com.example.likes.domain.CommentLike>> commentLikeAggRef = new AtomicReference<>(new ConcurrentHashMap<>());

    public ConcurrentHashMap<Long, CommentCountLike> getCommentAgg() { return commentAggRef.get(); }
    public ConcurrentHashMap<Integer, ActivityCountLike> getActivityAgg() { return activityAggRef.get(); }
    public ConcurrentHashMap<String, com.example.likes.domain.CommentLike> getCommentLikeAgg() { return commentLikeAggRef.get(); }

    public ConcurrentHashMap<Long, CommentCountLike> swapCommentAgg() {
        return commentAggRef.getAndSet(new ConcurrentHashMap<>());
    }
    public ConcurrentHashMap<Integer, ActivityCountLike> swapActivityAgg() {
        return activityAggRef.getAndSet(new ConcurrentHashMap<>());
    }
    public ConcurrentHashMap<String, com.example.likes.domain.CommentLike> swapCommentLikeAgg() {
        return commentLikeAggRef.getAndSet(new ConcurrentHashMap<>());
    }

    @RabbitListener(queues = "commentchannel")
    public void onCommentBatch(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        JsonNode items = root.get("items");
        if (items == null || !items.isArray()) return;
        for (JsonNode n : items) {
            long commentId = n.get("commentId").asLong();
            int activityId = n.get("activityId").asInt();
            long countLikes = n.get("countLikes").asLong();
            CommentCountLike c = new CommentCountLike();
            c.setCommentId(commentId);
            c.setActivityId(activityId);
            c.setCountLikes(countLikes);
            commentAggRef.get().put(commentId, c);

            JsonNode users = n.get("users");
            if (users != null && users.isArray()) {
                for (JsonNode u : users) {
                    long userId = u.asLong();
                    com.example.likes.domain.CommentLike cl = new com.example.likes.domain.CommentLike();
                    cl.setCommentId(commentId);
                    cl.setActivityId(activityId);
                    cl.setUserId(userId);
                    cl.setCreatedAt(new java.util.Date());
                    commentLikeAggRef.get().put(commentId + ":" + userId, cl);
                }
            }
        }
    }

    @RabbitListener(queues = "activitychannel")
    public void onActivityBatch(String payload) throws Exception {
        JsonNode root = mapper.readTree(payload);
        JsonNode items = root.get("items");
        if (items == null || !items.isArray()) return;
        for (JsonNode n : items) {
            int activityId = n.get("activityId").asInt();
            long countLikes = n.get("countLikes").asLong();
            ActivityCountLike a = new ActivityCountLike();
            a.setActivityId(activityId);
            a.setCountLikes(countLikes);
            activityAggRef.get().put(activityId, a);
        }
    }
}
