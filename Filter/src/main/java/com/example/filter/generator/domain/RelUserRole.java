package com.example.filter.generator.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @TableName rel_user_role
 */
@TableName(value ="rel_user_role")
@Data
public class RelUserRole {
    private Long userId;

    private Long roleId;
}