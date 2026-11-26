package com.tripmate.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

// 카카오·오피넷을 부를 때 쓴다. 바깥 서버가 느리면 역할 보드가 같이 멈추니 시간 제한을 둔다
@Configuration
public class RestTemplateConfig {

    private static final Duration TIMEOUT = Duration.ofSeconds(3);

    @Bean
    public RestTemplate restTemplate() {
        var factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(TIMEOUT);
        factory.setReadTimeout(TIMEOUT);
        return new RestTemplate(factory);
    }
}
