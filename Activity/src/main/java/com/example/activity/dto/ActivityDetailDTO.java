package com.example.activity.dto;

import com.example.activity.domain.Activities;
import com.example.activity.pojo.CommentESSave;
import lombok.Data;

import java.util.List;

@Data
public class ActivityDetailDTO {
    private Activities activity;
    private List<String> photos;
    private List<CommentESSave> comments;
}