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
    private final ElasticsearchOperations esOps;

    public LikeBatchFlusher(LikeBatchConsumer consumer,
                            CommentCountLikeService commentService,
                            ActivityCountLikeService activityService,
                            ElasticsearchOperations esOps,
                            com.example.likes.service.CommentLikeService commentLikeService) {
        this.consumer = consumer;
        this.commentService = commentService;
        this.activityService = activityService;
        this.esOps = esOps;
        this.commentLikeService = commentLikeService;
    }

    @Scheduled(fixedDelay = 30000)
    public void flush() {
        if (!consumer.getCommentAgg().isEmpty()) {
            List<CommentCountLike> comments = new ArrayList<>(consumer.getCommentAgg().values());
            commentService.upsertBatch(comments);
            if (!consumer.getCommentLikeAgg().isEmpty()) {
                List<com.example.likes.domain.CommentLike> likes = new ArrayList<>(consumer.getCommentLikeAgg().values());
                commentLikeService.insertIgnoreBatch(likes);
                consumer.getCommentLikeAgg().clear();
            }
            List<UpdateQuery> queries = new ArrayList<>();
            for (CommentCountLike c : comments) {
                Document doc = Document.create();
                doc.put("comment-all", c.getCountLikes());
                UpdateQuery uq = UpdateQuery.builder(String.valueOf(c.getCommentId()))
                        .withDocument(doc).build();
                queries.add(uq);
            }
            esOps.bulkUpdate(queries, IndexCoordinates.of("comments-all"));
            consumer.getCommentAgg().clear();
        }

        if (!consumer.getActivityAgg().isEmpty()) {
            List<ActivityCountLike> activities = new ArrayList<>(consumer.getActivityAgg().values());
            // 若需要 UPSERT，可为 ActivityCountLike 增加 Mapper upsertBatch；这里用 saveOrUpdateBatch 简化
            activityService.saveOrUpdateBatch(activities);
            List<UpdateQuery> queries = new ArrayList<>();
            for (ActivityCountLike a : activities) {
                Document doc = Document.create();
                doc.put("comment-all", a.getCountLikes());
                UpdateQuery uq = UpdateQuery.builder(String.valueOf(a.getActivityId()))
                        .withDocument(doc).build();
                queries.add(uq);
            }
            esOps.bulkUpdate(queries, IndexCoordinates.of("activity-all"));
            consumer.getActivityAgg().clear();
        }
    }
}
