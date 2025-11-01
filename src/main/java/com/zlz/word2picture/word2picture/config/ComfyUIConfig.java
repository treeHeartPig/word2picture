package com.zlz.word2picture.word2picture.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

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
}
