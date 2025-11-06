package com.zlz.word2picture.word2picture.controller;

import com.zlz.word2picture.word2picture.model.GenerateImageRequest;
import com.zlz.word2picture.word2picture.model.TaskResponse;
import com.zlz.word2picture.word2picture.service.ComfyUIService;
import com.zlz.word2picture.word2picture.service.TaskProgressService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/image")
public class ImageController {
    @Autowired
    private ComfyUIService comfyUIService;
    @Autowired
    private TaskProgressService taskProgressService;

    @PostMapping("/generate")
    public Mono<ResponseEntity<TaskResponse>> generateImage(@Valid @RequestBody GenerateImageRequest request) {
        log.info("工作流模板: {}", request.getWorkflowTemplate());

        return comfyUIService.generateImage(request)
                .map(taskResponse -> ResponseEntity.ok(taskResponse))
                .onErrorReturn(ResponseEntity.status(500).body(createErrorResponse("图像生成失败")));
    }
    @GetMapping(value = "/listening", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter listen(@RequestParam String taskId, @RequestParam String clientId) {
        SseEmitter register = taskProgressService.register(taskId);
        taskProgressService.startListening(clientId,taskId);
        return register;
    }
    @GetMapping("/task-status/{taskId}")
    public Mono<ResponseEntity<TaskResponse>> getTaskStatus(@PathVariable String taskId) {
        log.info("查询任务状态: {}", taskId);

        return comfyUIService.getTaskStatus(taskId)
                .map(taskResponse -> ResponseEntity.ok(taskResponse))
                .onErrorReturn(ResponseEntity.status(500).body(createErrorResponse("查询任务状态失败")));
    }

    private TaskResponse createErrorResponse(String message) {
        TaskResponse errorResponse = new TaskResponse();
        errorResponse.setStatus("ERROR");
        errorResponse.setMessage(message);
        return errorResponse;
    }

    @GetMapping("/result/{taskId}")
    public ResponseEntity<?> getResult(@PathVariable String taskId) {
        String imagePreviewUrl = comfyUIService.getImagePreviewUrl(taskId);
        System.out.println("----imageurl:"+imagePreviewUrl);
        Map<String,String> map = new HashMap<>();
        map.put("imageUrl", imagePreviewUrl);
        return ResponseEntity.ok(map);
    }
}