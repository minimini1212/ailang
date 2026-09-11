package com.example.ailang.global.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
@EnableJpaAuditing
@EnableCaching
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * FastAPI 호출에 쓰는 HTTP 클라이언트.
     *
     * 🔴 타임아웃을 반드시 건다. {@code new RestTemplate()} 의 기본 타임아웃은 «무한» 이라,
     *    AI 서버가 연결만 받고 응답을 안 주면 톰캣 스레드가 영원히 묶인다.
     *    서버가 죽지 않은 채로 멈춘다 — 가장 찾기 어려운 형태의 장애다.
     *
     * ⚠️ 읽기 타임아웃을 넉넉히 잡은 이유: 2026-09-01 실측에서 개념 설명이 **17~19초**,
     *    모의문제 생성이 **13초** 걸렸다. PRD §4 의 「AI API ≤ 5초」를 이미 넘고 있어
     *    값을 짧게 잡으면 정상 응답까지 끊긴다. 값은 설정으로 뺐다.
     *    🎯 응답 시간 자체를 줄이는 것은 별개 과제다 (TODOS.md 3절).
     */
    @Bean
    public RestTemplate restTemplate(@Value("${ai.server.connect-timeout-ms:5000}") long connectMs,
                                     @Value("${ai.server.read-timeout-ms:60000}") long readMs) {
        // ⚠️ RestTemplateBuilder 를 쓰지 않는다 — Spring Boot 4 에서 패키지가 옮겨졌다.
        //    이건 Spring Framework 쪽 클래스라 버전 이동에 안 흔들린다.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(connectMs));
        factory.setReadTimeout(Duration.ofMillis(readMs));
        return new RestTemplate(factory);
    }
}
