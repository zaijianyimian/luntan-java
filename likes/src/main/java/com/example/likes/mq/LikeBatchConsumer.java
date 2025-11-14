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

@Component
public class LikeBatchConsumer {

    private final ObjectMapper mapper = new ObjectMapper();
    private final ConcurrentHashMap<Long, CommentCountLike> commentAgg = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ActivityCountLike> activityAgg = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, com.example.likes.domain.CommentLike> commentLikeAgg = new ConcurrentHashMap<>();

    public ConcurrentHashMap<Long, CommentCountLike> getCommentAgg() { return commentAgg; }
    public ConcurrentHashMap<Integer, ActivityCountLike> getActivityAgg() { return activityAgg; }
    public ConcurrentHashMap<String, com.example.likes.domain.CommentLike> getCommentLikeAgg() { return commentLikeAgg; }

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
            commentAgg.put(commentId, c);

            JsonNode users = n.get("users");
            if (users != null && users.isArray()) {
                for (JsonNode u : users) {
                    long userId = u.asLong();
                    com.example.likes.domain.CommentLike cl = new com.example.likes.domain.CommentLike();
                    cl.setCommentId(commentId);
                    cl.setActivityId(activityId);
                    cl.setUserId(userId);
                    cl.setCreatedAt(new java.util.Date());
                    commentLikeAgg.put(commentId + ":" + userId, cl);
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
            activityAgg.put(activityId, a);
        }
    }
}
