package com.example.activity.dto;

import lombok.Data;

@Data
public class ChatResponseDTO {
    private String id;
    private String object;
    private Long created;
    private String model;
    private Choice[] choices;
    private Usage usage;

    @Data
    public static class Choice {
        private Integer index;
        private Message message;
        private String finish_reason;
    }

    @Data
    public static class Message {
        private String role;
        private String content;
    }

    @Data
    public static class Usage {
        private Integer prompt_tokens;
        private Integer completion_tokens;
        private Integer total_tokens;
    }
}