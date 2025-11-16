package com.example.likes.mq;

import com.example.likes.domain.CommentCountLike;
import com.example.likes.domain.ActivityCountLike;
import com.example.likes.service.CommentCountLikeService;
import com.example.likes.service.ActivityCountLikeService;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.document.Document;

import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class LikeBatchFlusher {

    private final LikeBatchConsumer consumer;
    private final CommentCountLikeService commentService;
    private final com.example.likes.service.CommentLikeService commentLikeService;
    private final ActivityCountLikeService activityService;
    private final com.example.likes.service.UserRelLikesService userRelLikesService;
    private final ElasticsearchOperations esOps;

    public LikeBatchFlusher(LikeBatchConsumer consumer,
                            CommentCountLikeService commentService,
                            ActivityCountLikeService activityService,
                            ElasticsearchOperations esOps,
                            com.example.likes.service.CommentLikeService commentLikeService,
                            com.example.likes.service.UserRelLikesService userRelLikesService) {
        this.consumer = consumer;
        this.commentService = commentService;
        this.activityService = activityService;
        this.esOps = esOps;
        this.commentLikeService = commentLikeService;
        this.userRelLikesService = userRelLikesService;
    }

    @Scheduled(fixedDelay = 30000)
    public void flush() {
        // 原子交换快照，避免与消费者并发写入产生竞态
        var commentSnapshot = consumer.swapCommentAgg();
        if (!commentSnapshot.isEmpty()) {
            List<CommentCountLike> comments = new ArrayList<>(commentSnapshot.values());
            commentService.upsertBatch(comments);
            var likeSnapshot = consumer.swapCommentLikeAgg();
            if (!likeSnapshot.isEmpty()) {
                List<com.example.likes.domain.CommentLike> likes = new ArrayList<>(likeSnapshot.values());
                commentLikeService.insertIgnoreBatch(likes);
            }
            var unlikeSnapshot = consumer.swapCommentUnlikeAgg();
            if (!unlikeSnapshot.isEmpty()) {
                List<com.example.likes.domain.CommentLike> unlikes = new ArrayList<>(unlikeSnapshot.values());
                try {
                    commentLikeService.deleteBatch(unlikes);
                } catch (Exception ignore) {}
            }
            List<UpdateQuery> queries = new ArrayList<>();
            for (CommentCountLike c : comments) {
                Document doc = Document.create();
                // 修正字段名为 countLikes
                doc.put("countLikes", c.getCountLikes());
                UpdateQuery uq = UpdateQuery.builder(String.valueOf(c.getCommentId()))
                        .withDocument(doc)
                        .withDocAsUpsert(true)
                        .build();
                queries.add(uq);
            }
            esOps.bulkUpdate(queries, IndexCoordinates.of("comments-all"));
        }

        var activitySnapshot = consumer.swapActivityAgg();
        if (!activitySnapshot.isEmpty()) {
            List<ActivityCountLike> activities = new ArrayList<>(activitySnapshot.values());
            // 若需要 UPSERT，可为 ActivityCountLike 增加 Mapper upsertBatch；这里用 saveOrUpdateBatch 简化
            activityService.saveOrUpdateBatch(activities);
            List<UpdateQuery> queries = new ArrayList<>();
            for (ActivityCountLike a : activities) {
                Document doc = Document.create();
                // 修正字段名为 countLikes
                doc.put("countLikes", a.getCountLikes());
                UpdateQuery uq = UpdateQuery.builder(String.valueOf(a.getActivityId()))
                        .withDocument(doc)
                        .withDocAsUpsert(true)
                        .build();
                queries.add(uq);
            }
            esOps.bulkUpdate(queries, IndexCoordinates.of("activity-all"));
        }

        var activityLikeSnapshot = consumer.swapActivityLikeAgg();
        if (!activityLikeSnapshot.isEmpty()) {
            List<com.example.likes.domain.UserRelLikes> rels = new ArrayList<>(activityLikeSnapshot.values());
            try {
                int n = userRelLikesService.insertIgnoreBatch(rels);
                org.slf4j.LoggerFactory.getLogger(LikeBatchFlusher.class).info("flushed user_rel_likes rows={} ", n);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(LikeBatchFlusher.class).warn("flush user_rel_likes failed: {}", e.getMessage());
            }
        }

        var activityUnlikeSnapshot = consumer.swapActivityUnlikeAgg();
        if (!activityUnlikeSnapshot.isEmpty()) {
            List<com.example.likes.domain.UserRelLikes> rels = new ArrayList<>(activityUnlikeSnapshot.values());
            try {
                int n = userRelLikesService.deleteBatch(rels);
                org.slf4j.LoggerFactory.getLogger(LikeBatchFlusher.class).info("deleted user_rel_likes rows={} ", n);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(LikeBatchFlusher.class).warn("delete user_rel_likes failed: {}", e.getMessage());
            }
        }
    }
}
