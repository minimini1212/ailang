package com.example.ailang.global.security.handler;

import com.example.ailang.global.jwt.JwtProperties;
import com.example.ailang.global.jwt.JwtTokenProvider;
import com.example.ailang.global.redis.RedisService;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import com.example.ailang.global.security.util.CookieUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;

@Component
@RequiredArgsConstructor
public class OAuth2SuccessHandler implements AuthenticationSuccessHandler {

    private final JwtTokenProvider jwtTokenProvider;
    private final RedisService redisService;
    private final CookieUtil cookieUtil;
    private final JwtProperties jwtProperties;

    @Value("${app.oauth2.redirect-uri}")
    private String redirectUri;

    private static final String REFRESH_KEY_PREFIX = "refresh:token:";

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        String email = userDetails.getEmail();

        String accessToken = jwtTokenProvider.createAccessToken(email);
        String refreshToken = jwtTokenProvider.createRefreshToken(email);

        redisService.save(REFRESH_KEY_PREFIX + email, refreshToken,
            Duration.ofMillis(jwtProperties.getRefreshExpiration()));

        cookieUtil.createAccessTokenCookie(response, accessToken);
        cookieUtil.createRefreshTokenCookie(response, refreshToken);

        response.sendRedirect(redirectUri);
    }
}
