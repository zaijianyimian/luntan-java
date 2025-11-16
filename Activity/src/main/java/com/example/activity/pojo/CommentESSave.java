package com.example.activity.pojo;

import com.example.activity.domain.Comments;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.elasticsearch.annotations.Document;

@Data
@NoArgsConstructor
@Document(indexName = "comments-all")
public class CommentESSave {

    private Integer id;
    private Long userId;
    private Integer activityId;
    private String description;
    private Long countLikes;
    private String userImage;

    public CommentESSave(Comments c) {
        this.id = c.getId();
        this.userId = c.getUserId();
        this.activityId = c.getActivityId();
        this.description = c.getDescription();
        this.countLikes = c.getCountLikes();
    }
}
