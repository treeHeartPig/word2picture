package com.zlz.word2picture.word2picture.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

import static com.zlz.word2picture.word2picture.constants.Constant.*;

@Configuration
public class ComfyUIConfig {

    @Value("${comfyui.api.base-url}")
    private String baseUrl;

    @Bean
    public WebClient comfyUIWebClient() {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Bean
    public MinioClient getMinioClient(){
        return MinioClient.builder()
                .endpoint(MINIO_URL)
                .credentials(MINIO_ADMIN, MINIO_PWD)
                .build();
    }
}
