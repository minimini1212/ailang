package com.example.ailang.domain.auth.service;

import com.example.ailang.domain.auth.exception.CodeAttemptsExceededException;
import com.example.ailang.domain.auth.exception.EmailSendRateLimitedException;
import com.example.ailang.domain.auth.exception.InvalidCodeException;
import com.example.ailang.domain.user.repository.UserRepository;
import com.example.ailang.global.mail.MailService;
import com.example.ailang.global.redis.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 인증 메일 발송·코드 확인의 <b>순서</b>를 못 박는다. Redis 도 메일 서버도 가짜다 —
 * 인프라 없이 돈다.
 *
 * <p>🔴 이 검사들이 잡는 변형은 전부 «한 줄을 위아래로 옮기는» 것이다. 옮겨도 컴파일되고
 * 대부분의 수동 확인도 통과하지만, 상한이 통째로 무력해진다.
 */
@DisplayName("이메일 인증 — 발송·시도 상한")
@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceImplTest {

    private static final String EMAIL = "student@example.com";
    private static final String CODE_KEY = "email:verify:code:" + EMAIL;
    private static final String SEND_KEY = "email:verify:send-count:" + EMAIL;
    private static final String ATTEMPT_KEY = "email:verify:attempt:" + EMAIL;

    @Mock RedisService redisService;
    @Mock MailService mailService;
    @Mock UserRepository userRepository;

    @InjectMocks EmailVerificationServiceImpl service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "verificationCodeTtl", 300L);
        ReflectionTestUtils.setField(service, "maxSendPerWindow", 5);
        ReflectionTestUtils.setField(service, "sendWindowSeconds", 3600L);
        ReflectionTestUtils.setField(service, "maxVerifyAttempts", 5);
    }

    @Nested
    @DisplayName("🔴 발송 상한")
    class Sending {

        @Test
        @DisplayName("상한을 넘으면 메일을 «보내지 않고» 거부한다")
        void 상한을_넘으면_메일을_안_보낸다() {
            // 🔴 잡는 변형: 상한 확인을 mailService 호출 «뒤» 로 옮기는 것.
            //    그러면 거부된 요청도 메일을 한 통씩 보내므로 상한이 폭탄을 못 막는다.
            given(redisService.increment(eq(SEND_KEY), any(Duration.class))).willReturn(6L);

            assertThatThrownBy(() -> service.sendCode(EMAIL))
                    .isInstanceOf(EmailSendRateLimitedException.class);

            verify(mailService, never()).sendVerificationCode(anyString(), anyString());
        }

        @Test
        @DisplayName("상한을 넘으면 가입 여부도 «묻지 않는다» — 무제한 가입자 명단 조회를 막는다")
        void 상한을_넘으면_가입여부를_안_본다() {
            // 🔴 잡는 변형: 상한 확인을 existsByEmail «뒤» 로 옮기는 것.
            //    그러면 「이미 가입된 이메일입니다」 응답으로 누가 가입했는지를
            //    횟수 제한 없이 확인할 수 있다.
            given(redisService.increment(eq(SEND_KEY), any(Duration.class))).willReturn(6L);

            assertThatThrownBy(() -> service.sendCode(EMAIL))
                    .isInstanceOf(EmailSendRateLimitedException.class);

            verify(userRepository, never()).existsByEmail(anyString());
        }

        @Test
        @DisplayName("상한 안이면 보내고, 이전 코드의 시도 횟수는 지운다")
        void 상한_안이면_보낸다() {
            // 코드를 다시 받은 학생이 첫 입력부터 막히면 안 된다.
            given(redisService.increment(eq(SEND_KEY), any(Duration.class))).willReturn(1L);
            given(userRepository.existsByEmail(EMAIL)).willReturn(false);

            service.sendCode(EMAIL);

            verify(mailService).sendVerificationCode(eq(EMAIL), anyString());
            verify(redisService).save(eq(CODE_KEY), anyString(), any(Duration.class));
            verify(redisService).delete(ATTEMPT_KEY);
        }
    }

    @Nested
    @DisplayName("🔴 코드 입력 시도 상한")
    class Verifying {

        @Test
        @DisplayName("틀린 입력도 «센다» — 대조보다 먼저 세지 않으면 상한이 없는 것과 같다")
        void 틀린_입력도_센다() {
            // 🔴 잡는 변형: increment 를 storedCode.equals(code) 비교 «뒤» 로 옮기는 것.
            //    틀린 입력이 안 세어지면 6자리 숫자를 끝까지 넣어 볼 수 있다.
            given(redisService.get(CODE_KEY)).willReturn(Optional.of("123456"));
            given(redisService.increment(eq(ATTEMPT_KEY), any(Duration.class))).willReturn(1L);

            assertThatThrownBy(() -> service.verifyCode(EMAIL, "000000"))
                    .isInstanceOf(InvalidCodeException.class);

            verify(redisService).increment(eq(ATTEMPT_KEY), any(Duration.class));
        }

        @Test
        @DisplayName("시도 상한을 넘으면 코드를 «버린다» — 안 버리면 기다렸다 이어서 넣으면 된다")
        void 상한을_넘으면_코드를_버린다() {
            // 🔴 잡는 변형: 코드 삭제를 빼는 것. 카운터만 만료되길 기다렸다가 같은 코드로
            //    이어서 넣으면 되므로, 센 의미가 사라진다.
            given(redisService.get(CODE_KEY)).willReturn(Optional.of("123456"));
            given(redisService.increment(eq(ATTEMPT_KEY), any(Duration.class))).willReturn(6L);

            assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456"))
                    .isInstanceOf(CodeAttemptsExceededException.class);

            verify(redisService).delete(CODE_KEY);
            verify(redisService).delete(ATTEMPT_KEY);
        }

        @Test
        @DisplayName("상한을 넘었으면 «맞는 코드를 넣어도» 통과시키지 않는다")
        void 상한을_넘으면_맞아도_거부한다() {
            // 🔴 위 검사와 같은 입력이지만 노리는 것이 다르다: 상한 확인이 대조 «뒤» 에
            //    있으면 맞는 코드는 그냥 통과하므로, 앞의 5번은 못 막은 것이 된다.
            given(redisService.get(CODE_KEY)).willReturn(Optional.of("123456"));
            given(redisService.increment(eq(ATTEMPT_KEY), any(Duration.class))).willReturn(6L);

            assertThatThrownBy(() -> service.verifyCode(EMAIL, "123456"))
                    .isInstanceOf(CodeAttemptsExceededException.class);

            verify(redisService, never()).save(startsWith("email:verified:"), anyString(), any(Duration.class));
        }

        @Test
        @DisplayName("맞으면 코드와 시도 횟수를 둘 다 지우고 인증 완료를 남긴다")
        void 맞으면_통과한다() {
            given(redisService.get(CODE_KEY)).willReturn(Optional.of("123456"));
            given(redisService.increment(eq(ATTEMPT_KEY), any(Duration.class))).willReturn(1L);

            service.verifyCode(EMAIL, "123456");

            verify(redisService).delete(CODE_KEY);
            verify(redisService).delete(ATTEMPT_KEY);
            verify(redisService).save(eq("email:verified:" + EMAIL), eq("true"), any(Duration.class));
        }
    }
}
