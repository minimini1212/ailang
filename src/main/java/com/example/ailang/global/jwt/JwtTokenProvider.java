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
        return createToken(email, jwtProperties.getAccessExpiration());
    }

    public String createRefreshToken(String email) {
        return createToken(email, jwtProperties.getRefreshExpiration());
    }

    private String createToken(String subject, long expiration) {
        Date now = new Date();
        return Jwts.builder()
            .setSubject(subject)
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + expiration))
            .signWith(getSigningKey(), SignatureAlgorithm.HS256)
            .compact();
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
