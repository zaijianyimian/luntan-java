package com.example.activity.feign;

import com.example.activity.dto.ChatRequestDTO;
import com.example.activity.dto.ChatResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(name = "longcat-chat", url = "https://api.longcat.chat")
public interface ServiceFeign {
    @PostMapping("/openai/v1/chat/completions")
    ChatResponseDTO chat(@RequestBody ChatRequestDTO request);
}