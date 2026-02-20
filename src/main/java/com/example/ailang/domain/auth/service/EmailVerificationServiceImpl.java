package com.example.ailang.domain.auth.service;

import com.example.ailang.domain.auth.exception.ExpiredCodeException;
import com.example.ailang.domain.auth.exception.InvalidCodeException;
import com.example.ailang.domain.user.exception.UserAlreadyExistsException;
import com.example.ailang.domain.user.repository.UserRepository;
import com.example.ailang.global.mail.MailService;
import com.example.ailang.global.redis.RedisService;
import com.example.ailang.global.security.util.CodeGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class EmailVerificationServiceImpl implements EmailVerificationService {

    private final RedisService redisService;
    private final MailService mailService;
    private final UserRepository userRepository;

    @Value("${app.mail.verification-code-ttl:300}")
    private long verificationCodeTtl;

    private static final String CODE_KEY_PREFIX = "email:verify:code:";
    private static final String VERIFIED_KEY_PREFIX = "email:verified:";
    private static final long VERIFIED_TTL_SECONDS = 30 * 60L;

    @Override
    public void sendCode(String email) {
        if (userRepository.existsByEmail(email)) {
            throw new UserAlreadyExistsException();
        }
        String code = CodeGenerator.generateNumeric(6);
        mailService.sendVerificationCode(email, code);
        redisService.save(CODE_KEY_PREFIX + email, code, Duration.ofSeconds(verificationCodeTtl));
    }

    @Override
    public void verifyCode(String email, String code) {
        String storedCode = redisService.get(CODE_KEY_PREFIX + email).orElseThrow(ExpiredCodeException::new);

        if (!storedCode.equals(code)) {
            throw new InvalidCodeException();
        }

        redisService.delete(CODE_KEY_PREFIX + email);
        redisService.save(VERIFIED_KEY_PREFIX + email, "true", Duration.ofSeconds(VERIFIED_TTL_SECONDS));
    }
}
