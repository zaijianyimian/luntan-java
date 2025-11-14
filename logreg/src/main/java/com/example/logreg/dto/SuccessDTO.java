package com.example.logreg.dto;


import com.example.logreg.message.Messages;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.Data;

@Data
@JsonPropertyOrder({"code", "message", "data"})
public class SuccessDTO<T> extends MessageDTO{

    T[] data;
    public SuccessDTO(T... data){
        super(Messages.SUCCESS);
        this.data = data.length > 0 ? data : null;
    }
}
