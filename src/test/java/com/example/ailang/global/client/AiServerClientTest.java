package com.example.ailang.global.client;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;
import com.example.ailang.global.jwt.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * AI 서버로 나가는 <b>유일한 문</b>. 여기서 세 가지가 지켜져야 한다.
 *
 * <ol>
 *   <li>실패의 <b>종류</b>가 살아서 학생에게 간다 — 429 / 502 / 503 이 각각 다른 뜻이다</li>
 *   <li>나가는 신원이 <b>서비스 토큰</b>이다 — 학생 토큰이면 반대편이 401 로 막는다</li>
 *   <li>나가는 몸통에 <b>개인정보가 없다</b> — 이메일·닉네임·id·답안 이력이 실리면 안 된다</li>
 * </ol>
 *
 * <p>🔴 <b>실제 AI 서버를 부르지 않는다.</b> {@code RestTemplate} 을 가짜로 바꿔 끼운다 —
 * 진짜 모델을 부르는 검사를 만들지 않는 것이 이 저장소의 규율이다. 그래서 이 검사는
 * 네트워크도 DB 도 없이 어디서나 돈다.
 *
 * <p>반대편(FastAPI)에서 같은 것을 재는 검사: {@code ai-server/tests/test_경계와_상태코드.py}
 */
@DisplayName("AI 서버 호출 — 실패의 종류·신원·개인정보")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AiServerClientTest {

    @Mock RestTemplate restTemplate;
    @Mock JwtTokenProvider jwtTokenProvider;

    AiServerClient client;

    @BeforeEach
    void setUp() {
        client = new AiServerClient(restTemplate, jwtTokenProvider);
        ReflectionTestUtils.setField(client, "aiServerUrl", "http://ai.invalid");
        given(jwtTokenProvider.createServiceToken(anyString())).willReturn("가짜-서비스-토큰");
    }

    private static HttpClientErrorException 상태(HttpStatus status) {
        return HttpClientErrorException.create(
                status, status.getReasonPhrase(), HttpHeaders.EMPTY,
                new byte[0], StandardCharsets.UTF_8);
    }

    private void 서버가_이렇게_실패한다(RuntimeException 실패) {
        given(restTemplate.postForObject(anyString(), any(), any(Class.class))).willThrow(실패);
    }

    @Nested
    @DisplayName("🔴 실패의 종류를 잃지 않는다")
    class FailureKinds {

        @Test
        @DisplayName("429 는 «잠시 후 다시» 로 살아서 나간다")
        void 한도_초과() {
            // 🔴 잡는 변형: 429 분기를 지우는 것. 그러면 학생은 「이용할 수 없습니다」만
            //    보고, 기다리면 되는 일인지 영영 안 되는 일인지 알 수 없다.
            서버가_이렇게_실패한다(상태(HttpStatus.TOO_MANY_REQUESTS));

            assertThatThrownBy(() -> client.requestConcept("q", "MIDDLE_1", "정수와 유리수"))
                    .isInstanceOf(ApplicationException.class)
                    .extracting(e -> ((ApplicationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AI_QUOTA_EXCEEDED);
        }

        @Test
        @DisplayName("502 는 «응답을 처리하지 못함» 으로 나간다")
        void 응답_형식_오류() {
            서버가_이렇게_실패한다(상태(HttpStatus.BAD_GATEWAY));

            assertThatThrownBy(() -> client.requestConcept("q", "MIDDLE_1", "정수와 유리수"))
                    .extracting(e -> ((ApplicationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AI_BAD_RESPONSE);
        }

        @ParameterizedTest(name = "HTTP {0} → 일시적으로 이용 불가")
        @ValueSource(ints = {401, 403, 404, 503})
        @DisplayName("나머지는 학생이 손쓸 수 없는 일로 분류한다")
        void 그_밖의_실패(int code) {
            서버가_이렇게_실패한다(상태(HttpStatus.valueOf(code)));

            assertThatThrownBy(() -> client.requestConcept("q", "MIDDLE_1", "정수와 유리수"))
                    .extracting(e -> ((ApplicationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AI_UNAVAILABLE);
        }

        @Test
        @DisplayName("연결 실패·타임아웃도 일시적으로 이용 불가다")
        void 못_닿음() {
            // ⚠️ 읽기 타임아웃이 60초다. 그 뒤에 여기로 온다.
            서버가_이렇게_실패한다(new ResourceAccessException("timeout", new IOException("read timed out")));

            assertThatThrownBy(() -> client.requestChat("q", "session-1", 7L))
                    .extracting(e -> ((ApplicationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AI_UNAVAILABLE);
        }

        @Test
        @DisplayName("🔴 빈 응답을 «개념 설명» 으로 둔갑시키지 않는다")
        void 빈_응답은_실패다() {
            // 🔴 잡는 변형: null 확인을 지우고 문장을 하나 돌려주는 옛 방식으로 되돌리는 것.
            //    그러면 화면에서 실패가 «선생님이 한 말» 처럼 보인다.
            given(restTemplate.postForObject(anyString(), any(), eq(AiServerClient.ConceptResponse.class)))
                    .willReturn(new AiServerClient.ConceptResponse());

            assertThatThrownBy(() -> client.requestConcept("q", "MIDDLE_1", "정수와 유리수"))
                    .extracting(e -> ((ApplicationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AI_BAD_RESPONSE);
        }

        @Test
        @DisplayName("🔴 빈 응답을 «답변» 으로 둔갑시키지 않는다 (챗봇)")
        void 빈_챗봇_응답도_실패다() {
            given(restTemplate.postForObject(anyString(), any(), eq(AiServerClient.ChatResponse.class)))
                    .willReturn(new AiServerClient.ChatResponse());

            assertThatThrownBy(() -> client.requestChat("q", "session-1", 7L))
                    .extracting(e -> ((ApplicationException) e).getErrorCode())
                    .isEqualTo(ErrorCode.AI_BAD_RESPONSE);
        }
    }

    @Nested
    @DisplayName("🔴 나가는 신원은 서비스 토큰이다")
    class Identity {

        @Test
        @DisplayName("학생 토큰을 만들지 않는다 — 만들면 반대편이 401 로 막는다")
        void 서비스_토큰을_쓴다() {
            // 🔴 잡는 변형: createServiceToken 을 createAccessToken 으로 되돌리는 것.
            //    예전에는 가짜 이메일(service@internal)로 학생 토큰과 «똑같은» 것을 만들어,
            //    둘을 구분할 방법이 없었다.
            given(restTemplate.postForObject(anyString(), any(), eq(AiServerClient.ConceptResponse.class)))
                    .willReturn(개념응답("설명"));

            client.requestConcept("q", "MIDDLE_1", "정수와 유리수");

            verify(jwtTokenProvider).createServiceToken(anyString());
            verify(jwtTokenProvider, never()).createAccessToken(anyString());
        }

        @Test
        @DisplayName("Authorization 헤더에 그 토큰을 싣는다")
        void 토큰을_헤더에_싣는다() {
            given(restTemplate.postForObject(anyString(), any(), eq(AiServerClient.ConceptResponse.class)))
                    .willReturn(개념응답("설명"));

            client.requestConcept("q", "MIDDLE_1", "정수와 유리수");

            assertThat(보낸_것().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                    .isEqualTo("Bearer 가짜-서비스-토큰");
        }
    }

    @Nested
    @DisplayName("🔴 개인정보를 모델 쪽으로 보내지 않는다")
    class NoPersonalData {

        @Test
        @DisplayName("개념 설명 요청에는 학년·단원·문제 본문만 실린다")
        void 개념_요청_몸통() {
            // 🔴 잡는 변형: 요청 DTO 에 이메일·닉네임·user id·답안 이력 필드를 더하는 것.
            //    이 검사는 «필드가 늘어나면» 빨간불이 된다 — 늘어난 것이 개인정보인지
            //    사람이 보게 만드는 것이 목적이다. 규율: CLAUDE.md 「Never send personal data」
            given(restTemplate.postForObject(anyString(), any(), eq(AiServerClient.ConceptResponse.class)))
                    .willReturn(개념응답("설명"));

            client.requestConcept("본문", "MIDDLE_1", "정수와 유리수");

            assertThat(보낸_것().getBody())
                    .hasFieldOrPropertyWithValue("question", "본문")
                    .hasFieldOrPropertyWithValue("grade", "MIDDLE_1")
                    .hasFieldOrPropertyWithValue("chapter_title", "정수와 유리수");
            assertThat(필드이름들(보낸_것().getBody()))
                    .containsExactlyInAnyOrder("question", "grade", "chapter_title");
        }

        @Test
        @DisplayName("모의문제 요청에는 단원·난이도·학년만 실린다")
        void 모의문제_요청_몸통() {
            given(restTemplate.postForObject(anyString(), any(), eq(AiServerClient.AiProblemData.class)))
                    .willReturn(new AiServerClient.AiProblemData());

            client.requestAiProblem("정수와 유리수", "MEDIUM", "MIDDLE_1");

            assertThat(필드이름들(보낸_것().getBody()))
                    .containsExactlyInAnyOrder("chapter_title", "difficulty", "grade");
        }

        @Test
        @DisplayName("챗봇 요청의 학생 식별자는 «서버가» 채운다")
        void 챗봇_요청_몸통() {
            // 🔴 대화를 누구 것으로 저장할지는 서버가 정한다. 클라이언트가 보낸 값을 쓰면
            //    남의 session id 를 넣어 남의 대화를 읽을 수 있다 (2026-09-01 에 닫힌 구멍).
            given(restTemplate.postForObject(anyString(), any(), eq(AiServerClient.ChatResponse.class)))
                    .willReturn(챗봇응답("답"));

            client.requestChat("질문", "session-1", 7L);

            assertThat(보낸_것().getBody())
                    .hasFieldOrPropertyWithValue("user_id", "7")
                    .hasFieldOrPropertyWithValue("session_id", "session-1");
            assertThat(필드이름들(보낸_것().getBody()))
                    .containsExactlyInAnyOrder("question", "session_id", "user_id");
        }
    }

    // ── 도우미 ────────────────────────────────────────────────────────────

    private HttpEntity<?> 보낸_것() {
        ArgumentCaptor<HttpEntity<?>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForObject(anyString(), captor.capture(), any(Class.class));
        return captor.getValue();
    }

    private static java.util.List<String> 필드이름들(Object body) {
        return java.util.Arrays.stream(body.getClass().getDeclaredFields())
                .filter(f -> !f.isSynthetic())
                .map(java.lang.reflect.Field::getName)
                .toList();
    }

    private static AiServerClient.ConceptResponse 개념응답(String concept) {
        AiServerClient.ConceptResponse r = new AiServerClient.ConceptResponse();
        r.setConcept(concept);
        return r;
    }

    private static AiServerClient.ChatResponse 챗봇응답(String answer) {
        AiServerClient.ChatResponse r = new AiServerClient.ChatResponse();
        r.setAnswer(answer);
        return r;
    }
}
