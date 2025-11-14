package com.example.filter.generator.domain;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * @TableName sys_role
 */
@TableName(value ="sys_role")
@Data
public class SysRole {
    private Long id;

    private String code;

    private String name;

    private Date createdAt;
}