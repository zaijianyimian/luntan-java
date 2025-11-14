package com.example.activity.dto;

import com.example.activity.message.Messages;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@JsonPropertyOrder({"code", "message", "data"})
public class SuccessDTO<T> extends MessageDTO{

    T[] data;
    @SafeVarargs
    public SuccessDTO(T... data){
        super(Messages.SUCCESS);
        this.data = data.length > 0 ? data : null;
    }
}
