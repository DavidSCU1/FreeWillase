package com.freewillase.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 关键修复：强制启用缓冲，确保 RestTemplate 发送 Content-Length 而不是 Chunked 编码
        factory.setBufferRequestBody(true);
        
        return builder
                .requestFactory(() -> factory)
                .setConnectTimeout(Duration.ofSeconds(30))
                .setReadTimeout(Duration.ofMinutes(10)) // MiniFold 预测较慢，设置 10 分钟超时
                .build();
    }
}
