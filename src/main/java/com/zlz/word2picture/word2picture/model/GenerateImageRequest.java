package com.zlz.word2picture.word2picture.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;


@Data
public class GenerateImageRequest {
    @NotBlank(message = "提示词不能为空")
    private String prompt;

    private String negativePrompt = "";

//    @Min(value = 1, message = "宽度必须大于0")
    private Integer width = 512;

//    @Min(value = 1, message = "高度必须大于0")
    private Integer height = 512;

//    @Min(value = 1, message = "步骤数必须大于0")
    private Integer steps = 20;

    private Double cfgScale = 8.0;

    private String samplerName = "euler";

    private String scheduler = "normal";

    private Integer seed = -1;

    @NotBlank(message = "工作流不能为空")
    private String workflowTemplate = "qwen-lora-海报.json";
}
