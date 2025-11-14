package com.example.logreg.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
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