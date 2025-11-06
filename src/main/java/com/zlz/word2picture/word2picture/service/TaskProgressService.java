package com.zlz.word2picture.word2picture.service;
// TaskProgressService.java

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zlz.word2picture.word2picture.model.TaskProgress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.client.ReactorNettyWebSocketClient;
import org.springframework.web.reactive.socket.client.WebSocketClient;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.*;

@Slf4j
@Service
public class TaskProgressService {

    private final WebSocketClient webSocketClient = new ReactorNettyWebSocketClient();

    private final Map<String, String> clientTaskMap = new ConcurrentHashMap<>();

    // 存储每个 taskId 对应的 SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();
    @Value("${comfyui.api.ws-url}")
    private String wsBaseUrl;

    // 使用 Flux + Sink 实现广播
    private final Flux<TaskProgress> progressFlux;
    private final FluxSink<TaskProgress> progressSink;

    private final ScheduledExecutorService scheduledExecutorService =
            Executors.newScheduledThreadPool(5);

    public TaskProgressService() {
        // 创建一个可广播的 Flux
        EmitterProcessor<TaskProgress> processor = EmitterProcessor.create();
        this.progressFlux = processor;
        this.progressSink = processor.sink();
    }

    @PreDestroy
    public void destroy() {
        progressSink.complete();
    }

    // 注册前端连接
    public SseEmitter register(String taskId) {
        // 设置更长的超时时间，比如 10 分钟
        SseEmitter emitter = new SseEmitter(10 * 60_000L); // 10分钟

        // 可选：添加心跳，每 15 秒发一次空消息保活
        Runnable heartbeat = () -> {
            try {
                emitter.send(SseEmitter.event().data("ping"));
            } catch (Exception e) {
                log.error("Error ping sending heartbeat: {}", e.getMessage());
                emitter.completeWithError(e);
            }
        };
        ScheduledFuture<?> timeoutCallback = scheduledExecutorService.scheduleAtFixedRate(
                heartbeat, 0, 15, TimeUnit.SECONDS
        );

        // 当连接完成、超时、出错时，取消心跳
        emitter.onCompletion(() -> timeoutCallback.cancel(true));
        emitter.onTimeout(() -> {
            log.error("Timeout for clientId: {}", taskId);
            emitter.complete();
            timeoutCallback.cancel(true);
        });
        emitter.onError(e -> {
            log.error("Error for clientId: {}", taskId, e);
            emitter.completeWithError(e);
            timeoutCallback.cancel(true);
        });

        emitters.put(taskId, emitter);
        return emitter;
    }

    // 接收来自 ComfyUI 的进度（由 listenProgress 调用）
    public void broadcastProgress(String taskId, String status, Integer progress, String detail) {
        TaskProgress update = new TaskProgress(taskId, status, progress, detail);
        progressSink.next(update); // 推送

        // 也推送给注册的 SseEmitter
        SseEmitter emitter = emitters.get(taskId);
        if (emitter != null) {
            try {
                System.out.println("-发送的消息："+ update);
                emitter.send(SseEmitter.event()
                        .name("progress")
                        .data(update));
            } catch (Exception e) {
                log.error("Error sending progress update", e);
                emitter.completeWithError(e);
                emitters.remove(taskId);
            }
        }
    }
    public void startListening(String clientId, String taskId) {
        clientTaskMap.put(clientId, taskId); // 映射 clientId -> taskId
        String url = String.format("%s?clientId=%s",wsBaseUrl,clientId);
        webSocketClient.execute(URI.create(url), session -> {
            String currentTaskId = clientTaskMap.get(clientId);
            return session.receive()
                    .map(WebSocketMessage::getPayloadAsText)
                    .doOnNext(message -> {
                        try {
                            ObjectMapper mapper = new ObjectMapper();
                            JsonNode node = mapper.readTree(message);
                            String type = node.get("type").asText();
                            System.out.println("----type:"+type);

                            switch (type) {
                                case "status":
                                    int remaining = node.get("data").get("status")
                                            .get("exec_info").get("queue_remaining").asInt();
                                    if (remaining == 0) {
                                        this.broadcastProgress(currentTaskId, "RUNNING", 0, "开始生成");
                                    }
                                    break;

                                case "progress":
                                    int value = node.get("data").get("value").asInt();
                                    int max = node.get("data").get("max").asInt();
                                    int progress = (int) (((double) value / max) * 100);
                                    this.broadcastProgress(currentTaskId, "RUNNING", progress, "生成中: " + progress + "%");
                                    break;

                                case "executing":
                                    JsonNode data = node.get("data");
                                    String nodeId = data.get("node").asText(null);
                                    if (nodeId == null) {
                                        broadcastProgress(currentTaskId, "SUCCESS", 100, "生成完成");
                                    } else {
                                        this.broadcastProgress(currentTaskId, "RUNNING", null, "执行节点: " + nodeId);
                                    }
                                    break;

                                case "execution_start":
                                    this.broadcastProgress(currentTaskId, "RUNNING", 0, "开始执行...");
                                    break;
                            }
                        } catch (Exception e) {
                            log.error("Error parsing message ", e);
                            this.broadcastProgress(currentTaskId, "FAILED", null, "解析错误: " + e.getMessage());
                        }
                    })
                    .then();
        }).subscribe();
    }
}
