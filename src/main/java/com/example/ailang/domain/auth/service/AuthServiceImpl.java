package com.example.ailang.domain.auth.service;

import com.example.ailang.domain.auth.dto.request.LoginRequest;
import com.example.ailang.domain.auth.dto.request.SignUpRequest;
import com.example.ailang.domain.auth.exception.EmailNotVerifiedException;
import com.example.ailang.domain.auth.exception.RefreshTokenExpiredException;
import com.example.ailang.domain.auth.exception.RefreshTokenNotFoundException;
import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.AuthProvider;
import com.example.ailang.domain.user.enums.UserRole;
import com.example.ailang.domain.user.enums.UserStatus;
import com.example.ailang.domain.user.exception.InvalidPasswordException;
import com.example.ailang.domain.user.exception.UserAlreadyExistsException;
import com.example.ailang.domain.user.exception.UserNotFoundException;
import com.example.ailang.domain.user.repository.UserRepository;
import com.example.ailang.global.exception.TokenExpiredException;
import com.example.ailang.global.jwt.JwtProperties;
import com.example.ailang.global.jwt.JwtTokenProvider;
import com.example.ailang.global.redis.RedisService;
import com.example.ailang.global.security.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtProperties jwtProperties;
    private final RedisService redisService;
    private final CookieUtil cookieUtil;

    private static final String VERIFIED_KEY_PREFIX = "email:verified:";
    private static final String REFRESH_KEY_PREFIX = "refresh:token:";
    // 로그아웃된 Access Token을 만료 전까지 차단하기 위한 블랙리스트 키 접두사
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:access:";

    @Override
    @Transactional
    public void signUp(SignUpRequest request) {
        if (!redisService.hasKey(VERIFIED_KEY_PREFIX + request.getEmail())) {
            throw new EmailNotVerifiedException();
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException();
        }

        User user = User.builder()
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .nickname(request.getNickname())
            .grade(request.getGrade())
            .provider(AuthProvider.LOCAL)
            .status(UserStatus.ACTIVE)
            .role(UserRole.ROLE_USER)
            .build();

        userRepository.save(user);
        redisService.delete(VERIFIED_KEY_PREFIX + request.getEmail());
    }

    @Override
    @Transactional(readOnly = true)
    public void login(LoginRequest request, HttpServletResponse response) {
        User user = userRepository.findByEmail(request.getEmail()).orElseThrow(UserNotFoundException::new);

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidPasswordException();
        }

        issueTokens(user.getEmail(), response);
    }

    @Override
    public void reissue(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieUtil.getRefreshToken(request).orElseThrow(RefreshTokenNotFoundException::new);

        try {
            jwtTokenProvider.validateToken(refreshToken);
        } catch (TokenExpiredException e) {
            throw new RefreshTokenExpiredException();
        }

        String email = jwtTokenProvider.getEmail(refreshToken);
        String storedToken = redisService.get(REFRESH_KEY_PREFIX + email).orElseThrow(RefreshTokenNotFoundException::new);

        if (!storedToken.equals(refreshToken)) {
            throw new RefreshTokenNotFoundException();
        }

        issueTokens(email, response);
    }

    @Override
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String accessToken = cookieUtil.getAccessToken(request).orElse(null);

        if (accessToken != null) {
            try {
                String email = jwtTokenProvider.getEmailIgnoreExpiry(accessToken);
                redisService.delete(REFRESH_KEY_PREFIX + email);

                // Access Token 블랙리스트 등록: 남은 만료 시간만큼 Redis에 유지 후 자동 삭제
                long remaining = jwtTokenProvider.getRemainingExpiration(accessToken);
                if (remaining > 0) {
                    redisService.save(BLACKLIST_KEY_PREFIX + accessToken, "logout", Duration.ofMillis(remaining));
                }
            } catch (Exception ignored) {}
        }

        cookieUtil.deleteAccessTokenCookie(response);
        cookieUtil.deleteRefreshTokenCookie(response);
    }

    private void issueTokens(String email, HttpServletResponse response) {
        String accessToken = jwtTokenProvider.createAccessToken(email);
        String refreshToken = jwtTokenProvider.createRefreshToken(email);

        redisService.save(REFRESH_KEY_PREFIX + email, refreshToken,
            Duration.ofMillis(jwtProperties.getRefreshExpiration()));

        cookieUtil.createAccessTokenCookie(response, accessToken);
        cookieUtil.createRefreshTokenCookie(response, refreshToken);
    }
}
