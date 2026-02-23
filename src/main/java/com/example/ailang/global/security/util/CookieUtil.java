package com.example.ailang.global.security.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class CookieUtil {

    public static final String ACCESS_TOKEN_COOKIE = "accessToken";
    public static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    @Value("${spring.profiles.active:local}")
    private String activeProfile;

    @Value("${jwt.access-expiration:1800000}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration:604800000}")
    private long refreshExpiration;

    public void createAccessTokenCookie(HttpServletResponse response, String token) {
        addCookie(response, ACCESS_TOKEN_COOKIE, token, (int) (accessExpiration / 1000));
    }

    public void createRefreshTokenCookie(HttpServletResponse response, String token) {
        addCookie(response, REFRESH_TOKEN_COOKIE, token, (int) (refreshExpiration / 1000));
    }

    public void deleteAccessTokenCookie(HttpServletResponse response) {
        addCookie(response, ACCESS_TOKEN_COOKIE, "", 0);
    }

    public void deleteRefreshTokenCookie(HttpServletResponse response) {
        addCookie(response, REFRESH_TOKEN_COOKIE, "", 0);
    }

    private void addCookie(HttpServletResponse response, String name, String value, int maxAge) {
        ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
            .httpOnly(true)
            .path("/")
            .maxAge(maxAge);

        if ("prod".equals(activeProfile)) {
            builder.sameSite("None").secure(true);
        } else {
            // 로컬 환경 CSRF 방어: 크로스 사이트 POST 요청 시 쿠키 전송 차단
            builder.sameSite("Lax");
        }

        response.addHeader("Set-Cookie", builder.build().toString());
    }

    public Optional<String> getAccessToken(HttpServletRequest request) {
        return getCookieValue(request, ACCESS_TOKEN_COOKIE);
    }

    public Optional<String> getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_COOKIE);
    }

    private Optional<String> getCookieValue(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
            .filter(cookie -> name.equals(cookie.getName()))
            .map(Cookie::getValue)
            .findFirst();
    }
}
