package com.zlz.word2picture.word2picture.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlz.word2picture.word2picture.model.GenerateImageRequest;
import com.zlz.word2picture.word2picture.model.TaskResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ComfyUIService {

    @Autowired
    private WebClient comfyUIWebClient;

    @Autowired
    private ObjectMapper objectMapper;

    // 用于存储任务状态
    private final Map<String, TaskResponse> taskStatus = new ConcurrentHashMap<>();

    public Mono<TaskResponse> generateImage(GenerateImageRequest request) {
        try {
            // 从resources目录加载工作流模板
            Map<String, Object> workflow = loadWorkflowFromResource(request.getWorkflowTemplate());

            // 动态替换工作流中的参数
            updateWorkflowParameters(workflow, request);

            // 构建请求体
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("prompt", workflow);
            String clientId = UUID.randomUUID().toString();
            requestBody.put("client_id", clientId);

            log.info("发送请求到ComfyUI，工作流: {}", workflow);
            return comfyUIWebClient.post()
                    .uri("/prompt")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(requestBody))
                    .retrieve()
                    .bodyToMono(String.class)
                    .map(response -> {
                        try {
                            Map<String, Object> responseMap = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
                            String taskId = (String) responseMap.get("prompt_id");

                            TaskResponse taskResponse = new TaskResponse();
                            taskResponse.setTaskId(taskId);
                            taskResponse.setClientId(clientId);
                            taskResponse.setStatus("SUBMITTED");
                            taskResponse.setMessage("图像生成任务已提交");
                            taskResponse.setTimestamp(System.currentTimeMillis());

                            // 存储任务状态
                            taskStatus.put(taskId, taskResponse);

                            log.info("任务提交成功，任务ID: {}", taskId);
                            return taskResponse;
                        } catch (Exception e) {
                            log.error("解析ComfyUI响应失败", e);
                            throw new RuntimeException("解析响应失败: " + e.getMessage());
                        }
                    })
                    .onErrorMap(e -> {
                        log.error("调用ComfyUI API失败", e);
                        throw new RuntimeException("调用ComfyUI失败: " + e.getMessage());
                    });
        } catch (Exception e) {
            log.error("生成图像请求失败", e);
            return Mono.error(new RuntimeException("生成图像请求失败: " + e.getMessage()));
        }
    }

    public Mono<TaskResponse> getTaskStatus(String taskId) {
        TaskResponse storedTask = taskStatus.get(taskId);
        if (storedTask == null) {
            TaskResponse notFound = new TaskResponse();
            notFound.setTaskId(taskId);
            notFound.setStatus("NOT_FOUND");
            notFound.setMessage("任务不存在");
            return Mono.just(notFound);
        }

        // 查询ComfyUI历史记录
        return comfyUIWebClient.get()
                .uri("/history/{taskId}", taskId)
                .retrieve()
                .bodyToMono(String.class)
                .map(response -> {
                    try {
                        Map<String, Object> history = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
                        Map<String, Object> taskHistory = (Map<String, Object>) history.get(taskId);

                        if (taskHistory == null) {
                            storedTask.setStatus("PENDING");
                            storedTask.setMessage("任务正在处理中");
                            return storedTask;
                        }

                        Map<String, Object> outputs = (Map<String, Object>) taskHistory.get("outputs");
                        if (outputs == null || outputs.isEmpty()) {
                            storedTask.setStatus("PROCESSING");
                            storedTask.setMessage("任务正在处理中");
                            return storedTask;
                        }

                        // 查找图像输出
                        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                            Map<String, Object> output = (Map<String, Object>) entry.getValue();
                            List<Map<String, String>> images = (List<Map<String, String>>) output.get("images");

                            if (images != null && !images.isEmpty()) {
                                Map<String, String> imageInfo = images.get(0);
                                String filename = imageInfo.get("filename");
                                String subfolder = imageInfo.get("subfolder");
                                String type = imageInfo.get("type");

                                String imageUrl = String.format("/view?filename=%s&subfolder=%s&type=%s",
                                        filename, subfolder != null ? subfolder : "", type != null ? type : "output");

                                storedTask.setStatus("COMPLETED");
                                storedTask.setMessage("图像生成完成");
                                storedTask.setImageUrl(imageUrl);
                                return storedTask;
                            }
                        }

                        storedTask.setStatus("PROCESSING");
                        storedTask.setMessage("任务正在处理中");
                        return storedTask;
                    } catch (Exception e) {
                        log.error("解析任务历史失败", e);
                        storedTask.setStatus("ERROR");
                        storedTask.setMessage("解析任务状态失败: " + e.getMessage());
                        return storedTask;
                    }
                })
                .switchIfEmpty(Mono.fromCallable(() -> {
                    storedTask.setStatus("PENDING");
                    storedTask.setMessage("任务正在排队中");
                    return storedTask;
                }))
                .onErrorReturn(storedTask);
    }

    /**
     * 从resources目录加载工作流JSON文件
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> loadWorkflowFromResource(String workflowFileName) throws IOException {
        ClassPathResource resource = new ClassPathResource("workflows/" + workflowFileName);

        if (!resource.exists()) {
            log.error("工作流文件不存在: {}", workflowFileName);
            throw new RuntimeException("工作流文件不存在: " + workflowFileName);
        }

        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, new TypeReference<Map<String, Object>>() {});
        }
    }

    /**
     * 根据请求参数动态更新工作流中的参数
     */
    private void updateWorkflowParameters(Map<String, Object> workflow, GenerateImageRequest request) {
        for (Map.Entry<String, Object> entry : workflow.entrySet()) {
            Object node = entry.getValue();
            //自定义提示词
            if(Objects.equals(entry.getKey(),"3")){
                if (node instanceof Map) {
                    updateNodeParameters((Map<String, Object>) node, request);
                }
            }
            //自定义长、高
            if(Objects.equals(entry.getKey(),"8")){
                if (node instanceof Map) {
                    updateNodeParameters((Map<String, Object>) node, request);
                }
            }

        }
    }

    /**
     * 更新单个节点的参数
     */
    @SuppressWarnings("unchecked")
    private void updateNodeParameters(Map<String, Object> node, GenerateImageRequest request) {
        String classType = (String) node.get("class_type");
        Map<String, Object> inputs = (Map<String, Object>) node.get("inputs");

        if (inputs == null) {
            return;
        }
        switch (classType){
            case "CLIPTextEncode":  inputs.put("text", request.getPrompt());break;
            case "EmptyLatentImage":
                inputs.put("width",request.getWidth()== null ? 300 : request.getWidth());
                inputs.put("height",request.getHeight()== null ? 500 : request.getHeight());
                break;
        }


//        switch (classType) {
//            case "CLIPLoader":
//
//            case "CLIPTextEncode":
//                // 更新正向和负向提示词
//                if (inputs.get("text") != null) {
//                    String text = (String) inputs.get("text");
//                    if (text.contains("positive_prompt_placeholder")) {
//                        inputs.put("text", text.replace("positive_prompt_placeholder", request.getPrompt()));
//                    } else if (text.contains("negative_prompt_placeholder")) {
//                        inputs.put("text", text.replace("negative_prompt_placeholder", request.getNegativePrompt()));
//                    } else {
//                        // 如果没有占位符，根据节点内容判断是正向还是负向提示词
//                        // 通常正向提示词节点在前，负向提示词节点连接到KSampler的negative输入
//                        inputs.put("text", request.getPrompt());
//                    }
//                }
//                break;
//
//            case "KSampler":
//                // 更新采样参数
//                inputs.put("steps", request.getSteps());
//                inputs.put("cfg", request.getCfgScale());
//                inputs.put("sampler_name", request.getSamplerName());
//                inputs.put("scheduler", request.getScheduler());
//
//                if (!request.getSeed().equals(-1)) {
//                    inputs.put("seed", request.getSeed());
//                } else {
//                    inputs.put("seed", (int) (Math.random() * 1000000000));
//                }
//                break;
//
//            case "EmptyLatentImage":
//                // 更新图像尺寸
//                inputs.put("width", request.getWidth());
//                inputs.put("height", request.getHeight());
//                break;
//
//            case "CheckpointLoaderSimple":
//                // 这里可以根据需要更新模型名称
//                // inputs.put("ckpt_name", request.getModel());
//                break;
//
//            default:
//                // 对于其他节点，可以添加自定义参数更新逻辑
//                break;
//        }
    }


    public String getImagePreviewUrl(String taskId) {
        try {
            // 查询ComfyUI历史记录获取图片信息
            String response = comfyUIWebClient.get()
                    .uri("/history/{taskId}", taskId)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(); // 同步获取响应

            if (response != null) {
                Map<String, Object> history = objectMapper.readValue(response, new TypeReference<Map<String, Object>>() {});
                Map<String, Object> taskHistory = (Map<String, Object>) history.get(taskId);

                if (taskHistory != null) {
                    Map<String, Object> outputs = (Map<String, Object>) taskHistory.get("outputs");
                    if (outputs != null && !outputs.isEmpty()) {
                        // 查找图像输出
                        for (Map.Entry<String, Object> entry : outputs.entrySet()) {
                            Map<String, Object> output = (Map<String, Object>) entry.getValue();
                            List<Map<String, String>> images = (List<Map<String, String>>) output.get("images");

                            if (images != null && !images.isEmpty()) {
                                Map<String, String> imageInfo = images.get(0);
                                String filename = imageInfo.get("filename");
                                String subfolder = imageInfo.get("subfolder");
                                String type = imageInfo.get("type");

                                // 构造预览URL
                                return String.format("http://localhost:8188/view?filename=%s&subfolder=%s&type=%s",
                                        filename,
                                        subfolder != null ? subfolder : "",
                                        type != null ? type : "output");
                            }
                        }
                    }
                }
            }
            return null;
        } catch (Exception e) {
            log.error("获取图片预览URL失败, taskId: {}", taskId, e);
            return null;
        }
    }

}
