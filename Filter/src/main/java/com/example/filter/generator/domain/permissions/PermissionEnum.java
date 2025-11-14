package com.example.filter.generator.domain.permissions;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum PermissionEnum {
    // 用户管理
    USER_STATUS_CHANGE("user:statuschange", "修改用户信息"),
    USER_DELETE("user:delete", "删除用户"),
    USER_BAN("user:ban", "封禁用户"),
    USER_CREATE("user:create", "创建用户"),
    USER_PROFILE("user:profile", "查看个人信息"),

    // 帖子管理
    POST_READ("post:read", "阅读帖子"),
    POST_LIKE("post:like", "点赞帖子"),
    POST_COMMENT("post:comment", "评论帖子"),
    POST_EDIT("post:edit", "编辑帖子"),
    POST_DELETE("post:delete", "删除帖子"),

    // 文件管理
    FILE_UPLOAD("file:upload", "上传文件"),
    FILE_DELETE("file:delete", "删除文件"),

    // 活动管理
    ACTIVITY_MANAGE("activity:manage", "管理活动"),
    ACTIVITY_VIEW("activity:view", "查看活动"),

    // 系统后台
    ADMIN_VIEW("admin:view", "查看后台界面"),
    REPORT_VIEW("report:view", "查看举报记录"),
    SYSTEM_CONFIG("system:config", "修改系统配置"),
    LOG_VIEW("log:view", "查看系统日志");


    private final String code;
    private final String desc;
}
