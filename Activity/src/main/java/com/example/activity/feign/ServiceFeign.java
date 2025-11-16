package com.example.activity.feign;

import com.example.activity.dto.ChatRequestDTO;
import com.example.activity.dto.ChatResponseDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
    name = "longcat-chat",
    url = "https://api.longcat.chat",
    configuration = LongCatFeignConfig.class
)
public interface ServiceFeign {

    @PostMapping(value = "/openai/v1/chat/completions", consumes = "application/json")
    ChatResponseDTO chat(@RequestBody ChatRequestDTO request);
}

