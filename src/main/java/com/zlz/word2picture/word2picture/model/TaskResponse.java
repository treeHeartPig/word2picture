package com.zlz.word2picture.word2picture.model;

import lombok.Data;

@Data
public class TaskResponse {
    private String taskId;
    private String clientId;
    private String status;
    private String message;
    private String imageUrl;
    private Long timestamp;
}