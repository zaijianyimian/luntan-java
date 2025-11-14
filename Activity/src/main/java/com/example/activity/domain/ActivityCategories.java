package com.example.activity.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @TableName activity_tags
 */
@TableName(value ="activity_categories")
@Data
public class ActivityCategories {
    private Integer id;

    private String tag;
}