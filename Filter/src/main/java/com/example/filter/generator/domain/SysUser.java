package com.example.filter.generator.domain;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id; // 主键ID

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 64, message = "用户名长度需在3-64之间")
    private String username; // 用户名

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 100, message = "密码长度必须在6-100之间")
    private String password; // 密码（BCrypt前）

    @Size(max = 50, message = "昵称长度不能超过50字符")
    private String nickname; // 昵称

    @Email(message = "邮箱格式不正确")
    private String email; // 邮箱

    @Min(0)
    @Max(1)
    private Integer status; // 账号状态：1正常,0禁用

    private Integer tokenVersion; // JWT版本

    @TableLogic
    private Integer deleted; // 逻辑删除：0未删,1已删

    @TableField("create_at")
    private LocalDateTime createAt; // 创建时间

    @TableField("updated_at")
    private LocalDateTime updatedAt; // 更新时间

}
