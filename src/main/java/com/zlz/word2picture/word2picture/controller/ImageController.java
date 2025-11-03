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
        log.info("收到图像生成请求: {}", request);

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

//    @GetMapping("/progress/{clientId}")
//    public String listenProgress(@PathVariable String clientId) {
//        AtomicReference<String> result = new AtomicReference<>("");
//        progressListener.startListening(clientId, message -> {
//            try {
//                ObjectMapper mapper = new ObjectMapper();
//                JsonNode node = mapper.readTree(message);
//                String type = node.get("type").asText();
//
//                switch (type) {
//                    case "status":
//                        int remaining = node.get("data").get("status")
//                                .get("exec_info").get("queue_remaining").asInt();
//                        System.out.println("[队列] 剩余任务: " + remaining);
//                        result.set("[队列] 剩余任务: " + remaining);
//                        break;
//
//                    case "progress":
//                        int value = node.get("data").get("value").asInt();
//                        int max = node.get("data").get("max").asInt();
//                        double progress = (double) value / max * 100;
//                        System.out.println("[进度] " + String.format("%.1f%%", progress));
//                        result.set("[进度] " + String.format("%.1f%%", progress));
//                        break;
//
//                    case "executing":
//                        JsonNode data = node.get("data");
//                        String nodeId = data.get("node").asText(null);
//                        if (nodeId == null) {
//                            System.out.println("[完成] 图像生成结束！");
//                            result.set("[完成] 图像生成结束！");
//                        } else {
//                            System.out.println("[执行] 正在处理节点: " + nodeId);
//                            result.set("[执行] 正在处理节点: " + nodeId);
//                        }
//                        break;
//
//                    case "execution_start":
//                        System.out.println("[开始] 执行工作流...");
//                        result.set("[开始] 执行工作流...");
//                        break;
//
//                    default:
//                        // 其他类型：executed, execution_cached 等
//                        break;
//                }
//
//            } catch (Exception e) {
//                System.err.println("解析消息失败: " + message);
//                result.set("解析消息失败: " + message);
//            }
//        });
//        return result.get();
//    }

    @GetMapping("/result/{taskId}")
    public ResponseEntity<?> getResult(@PathVariable String taskId) {
        String imagePreviewUrl = comfyUIService.getImagePreviewUrl(taskId);
        System.out.println("----imageurl:"+imagePreviewUrl);
        return ResponseEntity.ok(Map.of("imageUrl", imagePreviewUrl));
    }
}