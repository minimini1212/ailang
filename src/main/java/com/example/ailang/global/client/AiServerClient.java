package com.example.ailang.global.client;

import com.example.ailang.global.jwt.JwtTokenProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * FastAPI AI 서버 HTTP 클라이언트
 * - Spring Boot에서 FastAPI의 /ai/concept, /ai/problem 엔드포인트를 호출할 때 사용
 * - application.yml의 ai.server.url 값을 주입받아 사용
 */
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    // 내부 서비스 호출용 JWT 토큰을 Authorization 헤더에 포함한 HttpEntity 생성
    private HttpEntity<Object> withAuth(Object body) {
        String token = jwtTokenProvider.createAccessToken("service@internal");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * FastAPI POST /ai/concept 호출
     * - 문제 정보를 전달하면 Gemini가 관련 개념을 설명한 텍스트를 반환
     */
    public String requestConcept(String question, String grade, String chapterTitle) {
        String url = aiServerUrl + "/ai/concept";

        ConceptRequest request = new ConceptRequest(question, grade, chapterTitle);
        ConceptResponse response = restTemplate.postForObject(url, withAuth(request), ConceptResponse.class);

        return response != null ? response.getConcept() : "개념 설명을 불러오지 못했습니다.";
    }

    /**
     * FastAPI POST /ai/problem 호출
     * - 챕터명, 난이도, 학년을 전달하면 Gemini가 모의문제를 생성해 반환
     */
    public AiProblemData requestAiProblem(String chapterTitle, String difficulty, String grade) {
        String url = aiServerUrl + "/ai/problem";

        AiProblemRequest request = new AiProblemRequest(chapterTitle, difficulty, grade);
        AiProblemData response = restTemplate.postForObject(url, withAuth(request), AiProblemData.class);

        if (response == null) throw new RuntimeException("AI 모의문제 생성에 실패했습니다.");
        return response;
    }

    // FastAPI POST /ai/concept 요청 바디
    @Getter
    @RequiredArgsConstructor
    static class ConceptRequest {
        private final String question;
        private final String grade;
        private final String chapter_title;
    }

    // FastAPI POST /ai/concept 응답 바디
    @Getter
    @Setter
    static class ConceptResponse {
        private String concept;
    }

    // FastAPI POST /ai/problem 요청 바디
    @Getter
    @RequiredArgsConstructor
    static class AiProblemRequest {
        private final String chapter_title;
        private final String difficulty;
        private final String grade;
    }

    /**
     * FastAPI POST /ai/chat 호출
     * - 질문과 세션 ID를 전달하면 Gemini가 대화 맥락을 유지하며 답변 반환
     */
    public String requestChat(String question, String sessionId) {
        String url = aiServerUrl + "/ai/chat";
        ChatRequest request = new ChatRequest(question, sessionId);
        ChatResponse response = restTemplate.postForObject(url, withAuth(request), ChatResponse.class);
        return response != null ? response.getAnswer() : "응답을 받지 못했습니다.";
    }

    // FastAPI POST /ai/problem 응답 바디 (ProblemServiceImpl에서 접근하므로 public)
    @Getter
    @Setter
    public static class AiProblemData {
        private String question;
        private String problem_type;
        private String options;
        private String answer;
        private String explanation;
    }

    @Getter
    @RequiredArgsConstructor
    static class ChatRequest {
        private final String question;
        private final String session_id;
    }

    @Getter
    @Setter
    static class ChatResponse {
        private String answer;
        private String session_id;
    }
}
