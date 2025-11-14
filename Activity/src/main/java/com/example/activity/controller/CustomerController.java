package com.example.activity.controller;

import com.example.activity.dto.*;
import com.example.activity.feign.ServiceFeign;
import com.example.activity.message.Messages;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import static org.apache.naming.SelectorContext.prefix;

@RestController
@RequestMapping("/api")
@Slf4j
public class CustomerController {
    @Resource
    private ServiceFeign serviceFeign;



    @PostMapping("/activity/service")
    public MessageDTO getService(@RequestBody Content content){
        log.info("调用客服模块");
        String text = content.getContent();
        if(text == null || text.trim().isEmpty()){
            return new FailureDTO<>(Messages.NOT_FOUND, null);
        }
        ChatRequestDTO chatRequestDTO = new ChatRequestDTO(text);
        ChatResponseDTO chat = serviceFeign.chat(chatRequestDTO);
        if(chat == null){
            return new FailureDTO<>(Messages.INTERNAL_SERVER_ERROR, null);
        }
        return new SuccessDTO<>(chat);
    }
}