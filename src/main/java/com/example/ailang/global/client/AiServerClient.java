package com.example.ailang.global.client;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;
import com.example.ailang.global.jwt.JwtTokenProvider;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

/**
 * FastAPI AI 서버 HTTP 클라이언트
 * - Spring Boot에서 FastAPI의 /ai/concept, /ai/problem, /ai/chat 을 호출할 때 사용
 * - application.yml의 ai.server.url 값을 주입받아 사용
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiServerClient {

    private final RestTemplate restTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${ai.server.url}")
    private String aiServerUrl;

    // 서버 간 호출의 신원. FastAPI 가 이 이름으로 «누가 불렀나» 를 안다.
    private static final String SERVICE_NAME = "ailang-spring";

    // 내부 서비스 호출용 JWT 토큰을 Authorization 헤더에 포함한 HttpEntity 생성
    private HttpEntity<Object> withAuth(Object body) {
        // 🔴 학생 토큰이 아니라 «서비스 토큰» 이다. 종류가 달라서 학생용 API 로는 못 들어온다.
        //    예전에는 가짜 이메일(service@internal)로 학생 토큰과 똑같은 것을 만들었다.
        String token = jwtTokenProvider.createServiceToken(SERVICE_NAME);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /**
     * FastAPI 를 부르는 유일한 자리.
     *
     * 🔴 실패의 «종류»를 여기서 잃지 않는다. 예전에는 {@code RestTemplate} 이 던진 예외가
     *    {@code GlobalExceptionHandler} 의 {@code RuntimeException} 분기까지 흘러가
     *    「한도 초과」·「응답 형식 오류」·「서버 무응답」이 전부 500 «서버 내부 에러» 하나로
     *    보였다. 학생은 다시 시도해야 하는지 기다려야 하는지 알 수 없었다.
     *
     * 반대편의 분류: {@code ai-server/app/exceptions.py}
     * 규율: {@code docs/rules/ai-call-policy.md} R3
     */
    private <T> T call(String path, Object request, Class<T> responseType) {
        try {
            return restTemplate.postForObject(aiServerUrl + path, withAuth(request), responseType);

        } catch (HttpStatusCodeException e) {
            HttpStatusCode status = e.getStatusCode();
            log.warn("[AiServerClient] {} 실패 - HTTP {}", path, status.value());

            if (status.value() == HttpStatus.TOO_MANY_REQUESTS.value()) {
                throw new ApplicationException(ErrorCode.AI_QUOTA_EXCEEDED);
            }
            if (status.value() == HttpStatus.BAD_GATEWAY.value()) {
                throw new ApplicationException(ErrorCode.AI_BAD_RESPONSE);
            }
            // 401·503 등 나머지는 학생이 손쓸 수 없는 일이다.
            throw new ApplicationException(ErrorCode.AI_UNAVAILABLE);

        } catch (ResourceAccessException e) {
            // 타임아웃·연결 실패. AI 서버가 안 떠 있거나 응답이 너무 느리다.
            log.warn("[AiServerClient] {} 연결 실패: {}", path, e.getMessage());
            throw new ApplicationException(ErrorCode.AI_UNAVAILABLE);
        }
    }

    /**
     * FastAPI POST /ai/concept 호출
     * - 문제 정보를 전달하면 모델이 관련 개념을 설명한 텍스트를 반환
     */
    public String requestConcept(String question, String grade, String chapterTitle) {
        ConceptResponse response = call("/ai/concept",
                new ConceptRequest(question, grade, chapterTitle), ConceptResponse.class);

        // 🔴 예전에는 여기서 "개념 설명을 불러오지 못했습니다." 라는 «문장»을 돌려줬다.
        //    그러면 화면에서 실패가 개념 설명처럼 보인다. 실패는 실패로 알린다.
        if (response == null || response.getConcept() == null) {
            throw new ApplicationException(ErrorCode.AI_BAD_RESPONSE);
        }
        return response.getConcept();
    }

    /**
     * FastAPI POST /ai/problem 호출
     * - 챕터명, 난이도, 학년을 전달하면 모델이 모의문제를 생성해 반환
     */
    public AiProblemData requestAiProblem(String chapterTitle, String difficulty, String grade) {
        AiProblemData response = call("/ai/problem",
                new AiProblemRequest(chapterTitle, difficulty, grade), AiProblemData.class);

        if (response == null) {
            throw new ApplicationException(ErrorCode.AI_BAD_RESPONSE);
        }
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
    public String requestChat(String question, String sessionId, Long userId) {
        ChatResponse response = call("/ai/chat",
                new ChatRequest(question, sessionId, String.valueOf(userId)), ChatResponse.class);

        // 🔴 실패를 답변처럼 돌려주지 않는다. 학생 화면에서 「응답을 받지 못했습니다」가
        //    선생님이 한 말처럼 보이면 안 된다.
        if (response == null || response.getAnswer() == null) {
            throw new ApplicationException(ErrorCode.AI_BAD_RESPONSE);
        }
        return response.getAnswer();
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
        // 🔴 대화를 누구 것으로 저장할지 정하는 값. 서버가 채운다.
        private final String user_id;
    }

    @Getter
    @Setter
    static class ChatResponse {
        private String answer;
        private String session_id;
    }
}
