package com.example.activity.controller;

import com.example.activity.dto.*;
import com.example.activity.feign.ServiceFeign;
import com.example.activity.message.Messages;
import feign.FeignException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
        try { String json = new ObjectMapper().writeValueAsString(chatRequestDTO); log.info("客服请求体: {}", json); } catch (Exception ignore) {}
        try {
            ChatResponseDTO chat = serviceFeign.chat(chatRequestDTO);
            if (chat == null) {
                return new FailureDTO<>(Messages.INTERNAL_SERVER_ERROR, null);
            }
            return new SuccessDTO<>(chat);
        } catch (FeignException e) {
            log.warn("客服接口调用失败, status={}, body={}", e.status(), e.contentUTF8());
            log.warn("客服接口异常信息: {}", e.getMessage());
            Messages msg = mapStatus(e.status());
            Object detail = parseErrorBody(e.contentUTF8());
            return new FailureDTO<>(msg, detail);
        } catch (Exception e) {
            log.error("客服接口异常", e);
            return new FailureDTO<>(Messages.INTERNAL_SERVER_ERROR, null);
        }
    }

    private Messages mapStatus(int status){
        if (status == 400) return Messages.BAD_REQUEST;
        if (status == 401) return Messages.UNAUTHORIZED;
        if (status == 403) return Messages.FORBIDDEN;
        if (status == 404) return Messages.NOT_FOUND;
        if (status == 405) return Messages.METHOD_NOT_ALLOWED;
        if (status == 408) return Messages.REQUEST_TIMEOUT;
        if (status == 409) return Messages.CONFLICT;
        if (status == 410) return Messages.GONE;
        if (status == 412) return Messages.PRECONDITION_FAILED;
        if (status == 411) return Messages.LENGTH_REQUIRED;
        if (status == 422) return Messages.UNPROCESSABLE_ENTITY;
        if (status == 429) return Messages.SERVICE_UNAVAILABLE;
        if (status == 502) return Messages.BAD_GATEWAY;
        if (status == 503) return Messages.SERVICE_UNAVAILABLE;
        if (status == 504) return Messages.SERVER_ERROR;
        if (status == -1) return Messages.REQUEST_TIMEOUT;
        return Messages.INTERNAL_SERVER_ERROR;
    }

    private Object parseErrorBody(String body){
        if (body == null || body.isBlank()) return null;
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode node = mapper.readTree(body);
            if (node.has("error")) {
                JsonNode err = node.get("error");
                if (err.has("message")) return err.get("message").asText();
                return err.toString();
            }
            if (node.has("message")) return node.get("message").asText();
            return node.toString();
        } catch (Exception ignore) {
            return body;
        }
    }
}
