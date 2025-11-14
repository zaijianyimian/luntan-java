package com.example.activity.es.comments;

import com.example.activity.domain.Comments;
import com.example.activity.pojo.CommentESSave;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentsEs extends ElasticsearchRepository<CommentESSave,Integer> {
    public List<CommentESSave> findByActivityId(Integer activityId);

}
