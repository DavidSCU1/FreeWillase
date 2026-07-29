package com.freewillase.backend.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * 应用级基础配置。
 *
 * <p>这里最关键的是 RestTemplate 的请求体发送策略与超时：</p>
 * <ul>
 *   <li>对部分上游服务，Chunked 编码可能触发兼容性问题，因此强制使用缓冲发送（携带 Content-Length）。</li>
 *   <li>预测服务可能耗时较长，需要较大的 readTimeout 以避免中途被客户端超时中断。</li>
 * </ul>
 */
@Configuration
public class AppConfig {

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // 强制启用缓冲：让 RestTemplate 发送 Content-Length，而不是 Chunked 传输编码（提升对上游接口的兼容性）
        factory.setBufferRequestBody(true);
        
        return builder
                .requestFactory(() -> factory)
                // 连接超时：避免网络不可达时一直卡住
                .setConnectTimeout(Duration.ofSeconds(30))
                // 读取超时：预测类请求可能较慢（尤其是本地/云端推理链路），设置 10 分钟作为上限
                .setReadTimeout(Duration.ofMinutes(10))
                .build();
    }
}
