package com.example.ailang.global.jwt;

import com.example.ailang.global.exception.TokenExpiredException;
import com.example.ailang.global.exception.TokenInvalidException;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    private Key getSigningKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String createAccessToken(String email) {
        return createToken(email, TokenType.ACCESS, jwtProperties.getAccessExpiration());
    }

    public String createRefreshToken(String email) {
        return createToken(email, TokenType.REFRESH, jwtProperties.getRefreshExpiration());
    }

    /**
     * 서버가 서버를 부를 때 쓰는 토큰. 학생 토큰과 종류가 다르다.
     * 🔴 이 토큰으로 학생용 API 에 들어올 수 없다 — 인증 필터가 ACCESS 만 받는다.
     */
    public String createServiceToken(String serviceName) {
        return createToken(serviceName, TokenType.SERVICE, jwtProperties.getAccessExpiration());
    }

    private String createToken(String subject, TokenType type, long expiration) {
        Date now = new Date();
        return Jwts.builder()
            .setSubject(subject)
            .claim(TokenType.CLAIM, type.name())
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + expiration))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
    }

    /**
     * 토큰이 기대한 종류인지 확인한다. 아니면 «유효하지 않은 토큰» 이다.
     *
     * ⚠️ 종류 claim 이 아예 없는 토큰은 이 변경 전에 발급된 것이다. 거부한다 —
     *    받아 주면 구분이 없던 시절의 토큰이 그대로 통과해 고친 의미가 없어진다.
     *    기존 로그인 세션은 끊기고 다시 로그인해야 한다.
     */
    public void requireType(String token, TokenType expected) {
        Object raw = parseClaims(token).get(TokenType.CLAIM);
        if (raw == null || !expected.name().equals(raw.toString())) {
            throw new TokenInvalidException();
        }
    }

    public String getEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public String getEmailIgnoreExpiry(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody()
                .getSubject();
        } catch (ExpiredJwtException e) {
            return e.getClaims().getSubject();
        } catch (JwtException e) {
            throw new TokenInvalidException();
        }
    }

    public void validateToken(String token) {
        parseClaims(token);
    }

    // 블랙리스트 TTL 계산용: Access Token의 남은 만료 시간(ms) 반환
    public long getRemainingExpiration(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (TokenExpiredException e) {
            return 0;
        }
    }

    private Claims parseClaims(String token) {
        try {
            return Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        } catch (ExpiredJwtException e) {
            throw new TokenExpiredException();
        } catch (JwtException e) {
            throw new TokenInvalidException();
        }
    }
}
