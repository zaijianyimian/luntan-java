package com.example.logreg.dto;

import com.example.logreg.message.Messages;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"code", "message", "data"})
public class FailureDTO<T> extends MessageDTO {
    T data;
    public FailureDTO(Messages message, T data) {
        super(message);
        this.data = data;
    }
}
