package com.example.logreg.generator.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * @TableName rel_role_perm
 */
@TableName(value ="rel_role_perm")
@Data
public class RelRolePerm {
    private Long roleId;

    private Long permId;
}