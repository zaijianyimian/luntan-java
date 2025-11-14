package com.example.logreg.message;

public enum Messages {
    SUCCESS(200, "成功"),
    NOT_FOUND(404, "资源未找到"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    FORBIDDEN(403, "禁止访问"),
    BAD_REQUEST(400, "请求错误"),
    UNAUTHORIZED(401, "未授权"),
    METHOD_NOT_ALLOWED(405, "方法不被允许"),
    CONFLICT(409, "请求冲突"),
    GONE(410, "资源已被删除"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    SERVER_ERROR(504, "服务错误"),
    // 补充的一些常见错误码
    UNPROCESSABLE_ENTITY(422, "无法处理的实体"),
    REQUEST_TIMEOUT(408, "请求超时"),
    NOT_IMPLEMENTED(501, "未实现"),
    BAD_GATEWAY(502, "网关错误"),
    PRECONDITION_FAILED(412, "先决条件失败"),
    LENGTH_REQUIRED(411, "长度要求");

    private Integer code;
    private String message;

    // 构造函数
    Messages(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    // 获取错误码
    public Integer getCode() {
        return code;
    }

    // 设置错误码
    public void setCode(Integer code) {
        this.code = code;
    }

    // 获取错误消息
    public String getMessage() {
        return message;
    }

    // 设置错误消息
    public void setMessage(String message) {
        this.message = message;
    }
}
