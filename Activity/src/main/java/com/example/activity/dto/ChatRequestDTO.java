package com.example.activity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class ChatRequestDTO {
    private String model;
    private java.util.List<Message> messages;
    private boolean stream;
    @com.fasterxml.jackson.annotation.JsonProperty("max_tokens")
    private int maxTokens;
    private double temperature;

    public ChatRequestDTO(String content){
        this.model = "LongCat-Flash-Chat";
        this.messages = java.util.List.of(new Message("user", content));
        this.stream = false;
        this.maxTokens = 1000;
        this.temperature = 0.7;
    }

    @Data
    @AllArgsConstructor
    static class Message {
        private String role;
        private String content;
    }
}