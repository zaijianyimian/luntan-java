package com.example.activity.dto;

import com.example.activity.message.Messages;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"code", "message", "data"})
public class MessageDTO {
    private int code;
    private String message;

    // 默认构造函数
    public MessageDTO() {
    }

    // 添加接受Messages参数的构造函数
    public MessageDTO(Messages messages) {
        this.code = messages.getCode();
        this.message = messages.getMessage();
    }

    // getter和setter方法
    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
