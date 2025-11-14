package com.example.activity.dto;

import com.example.activity.message.Messages;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"code", "message", "data"})
public class FailureDTO<T> extends MessageDTO{
    T data;
    public FailureDTO(Messages message, T data) {
        super(message);
        this.data = data;
    }
}
