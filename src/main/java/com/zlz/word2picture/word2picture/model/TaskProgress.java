package com.zlz.word2picture.word2picture.model;

import lombok.Data;

@Data
public class TaskProgress {
    private String taskId;
    private String status;     // PENDING, RUNNING, SUCCESS, FAILED
    private Integer progress;  // 0-100
    private String detail;     // 当前节点、提示信息

    // 构造函数
    public TaskProgress(String taskId, String status, Integer progress, String detail) {
        this.taskId = taskId;
        this.status = status;
        this.progress = progress;
        this.detail = detail;
    }

}
