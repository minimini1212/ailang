package com.example.ailang.domain.auth.service;

import com.example.ailang.domain.auth.exception.CodeAttemptsExceededException;
import com.example.ailang.domain.auth.exception.EmailSendRateLimitedException;
import com.example.ailang.domain.auth.exception.ExpiredCodeException;
import com.example.ailang.domain.auth.exception.InvalidCodeException;
import com.example.ailang.domain.user.exception.UserAlreadyExistsException;
import com.example.ailang.domain.user.repository.UserRepository;
import com.example.ailang.global.mail.MailService;
import com.example.ailang.global.redis.RedisService;
import com.example.ailang.global.security.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * 이메일 인증 코드 발송·확인.
 *
 * <pre>
 *   발송   [상한 확인] → 가입 여부 → 코드 생성 → 메일 → Redis 저장
 *                ↑ 🔴 먼저다. 뒤에 두면 상한에 걸린 요청도 메일을 한 통 보낸 뒤 막힌다.
 *
 *   확인   코드 조회 → [시도 횟수 +1] → 초과면 코드 폐기 → 대조 → 인증 완료 표시
 *                          ↑ 대조 «전» 이다. 뒤에 두면 맞을 때까지 세지 않고 통과한다.
 * </pre>
 *
 * <p>🔴 <b>상한이 하나도 없었다.</b> 6자리 숫자를 무제한으로 넣어 볼 수 있었고, 발송
 * 엔드포인트는 로그인 없이 부를 수 있어 임의 주소로 메일을 무한히 쏠 수 있었다.
 * ⚠️ <b>상한 «값» 은 사용자 결정 대기 중</b>이다 (TODOS.md 8절). 그래서 코드에 박지 않고
 * {@code application.yml} 의 {@code app.mail.*} 로 뺐다 — 바꾸는 데 재배포가 필요 없다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final RedisService redisService;
    private final MailService mailService;
    private final UserRepository userRepository;

    @Value("${app.mail.verification-code-ttl:300}")
    private long verificationCodeTtl;

    /** 한 주소에 이 시간 동안 보낼 수 있는 메일 수. 기본값의 근거는 클래스 주석 참고. */
    @Value("${app.mail.max-send-per-window:5}")
    private int maxSendPerWindow;

    /** 위 상한을 세는 창의 길이(초). */
    @Value("${app.mail.send-window-seconds:3600}")
    private long sendWindowSeconds;

    /** 코드 하나에 허용하는 입력 횟수. */
    @Value("${app.mail.max-verify-attempts:5}")
    private int maxVerifyAttempts;

    private static final String CODE_KEY_PREFIX = "email:verify:code:";
    private static final String VERIFIED_KEY_PREFIX = "email:verified:";
    private static final String SEND_COUNT_KEY_PREFIX = "email:verify:send-count:";
    private static final String ATTEMPT_KEY_PREFIX = "email:verify:attempt:";
    private static final long VERIFIED_TTL_SECONDS = 30 * 60L;

    @Override
    public void sendCode(String email) {
        // 🔴 상한을 «가장 먼저» 본다. 가입 여부 확인보다도 앞이다 — 뒤에 두면 남의 주소가
        //    가입돼 있는지를 무제한으로 물어볼 수 있어, 상한이 가입자 명단 조회를 못 막는다.
        long sent = redisService.increment(
                SEND_COUNT_KEY_PREFIX + email, Duration.ofSeconds(sendWindowSeconds));
        if (new AttemptLimit(maxSendPerWindow).isExceeded(sent)) {
            log.warn("[인증메일] 발송 상한 초과 — 주소 {}, {}초 동안 {}번째 요청 (상한 {})",
                    email, sendWindowSeconds, sent, maxSendPerWindow);
            throw new EmailSendRateLimitedException();
        }

        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException();
        }
        String code = CodeGenerator.generateNumeric(6);
        mailService.sendVerificationCode(email, code);
        redisService.save(CODE_KEY_PREFIX + email, code, Duration.ofSeconds(verificationCodeTtl));

        // 새 코드를 냈으면 이전 코드의 시도 횟수는 의미가 없다. 남겨 두면 코드를 다시 받은
        // 학생이 첫 입력부터 막힌다.
        redisService.delete(ATTEMPT_KEY_PREFIX + email);
    }

    @Override
    public void verifyCode(String email, String code) {
        String storedCode = redisService.get(CODE_KEY_PREFIX + email).orElseThrow(ExpiredCodeException::new);

        // 🔴 대조 «전» 에 센다. 뒤에 두면 틀린 입력은 세지 않고 지나가므로 상한이 없는 것과 같다.
        //    만료는 코드와 같은 수명으로 둔다 — 코드가 사라지면 세던 것도 같이 의미를 잃는다.
        long attempts = redisService.increment(
                ATTEMPT_KEY_PREFIX + email, Duration.ofSeconds(verificationCodeTtl));
        if (new AttemptLimit(maxVerifyAttempts).isExceeded(attempts)) {
            // 코드를 «버린다». 안 버리면 상한이 만료될 때까지 기다렸다가 같은 코드로 이어서
            // 넣으면 되므로, 횟수를 센 의미가 사라진다.
            redisService.delete(CODE_KEY_PREFIX + email);
            redisService.delete(ATTEMPT_KEY_PREFIX + email);
            log.warn("[인증코드] 시도 상한 초과 — 주소 {}, {}번째 입력 (상한 {})",
                    email, attempts, maxVerifyAttempts);
            throw new CodeAttemptsExceededException();
        }

        if (!storedCode.equals(code)) {
            throw new InvalidCodeException();
        }

        redisService.delete(CODE_KEY_PREFIX + email);
        redisService.delete(ATTEMPT_KEY_PREFIX + email);
        redisService.save(VERIFIED_KEY_PREFIX + email, "true", Duration.ofSeconds(VERIFIED_TTL_SECONDS));
    }
}
