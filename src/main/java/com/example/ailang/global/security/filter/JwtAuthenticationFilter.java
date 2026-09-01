package com.example.ailang.global.security.filter;

import com.example.ailang.global.exception.TokenExpiredException;
import com.example.ailang.global.exception.TokenInvalidException;
import com.example.ailang.global.jwt.JwtTokenProvider;
import com.example.ailang.global.jwt.TokenType;
import com.example.ailang.global.redis.RedisService;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import com.example.ailang.global.security.userdetails.CustomUserDetailsService;
import com.example.ailang.global.security.util.CookieUtil;
import com.example.ailang.global.security.util.CustomResponseUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final CookieUtil cookieUtil;
    private final RedisService redisService;

    // 블랙리스트 키 접두사 (AuthServiceImpl과 동일하게 유지)
    private static final String BLACKLIST_KEY_PREFIX = "blacklist:access:";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = cookieUtil.getAccessToken(request).orElse(null);

        if (token != null) {
            try {
                jwtTokenProvider.validateToken(token);

                // 🔴 «액세스 토큰인지» 를 반드시 확인한다.
                //    예전에는 서명과 만료만 봐서, 리프레시 토큰을 이 쿠키 자리에 넣으면
                //    모든 API 가 통과했다. 로그아웃은 액세스 토큰만 블랙리스트에 넣으므로
                //    유출된 리프레시 토큰이 만료(7일)까지 계속 살아 있었다.
                //    서비스 토큰(서버 간 호출용)도 여기서 막힌다.
                jwtTokenProvider.requireType(token, TokenType.ACCESS);

                // 로그아웃된 토큰인지 블랙리스트 확인
                if (redisService.hasKey(BLACKLIST_KEY_PREFIX + token)) {
                    CustomResponseUtil.fail(response, "로그아웃된 토큰입니다.", HttpStatus.UNAUTHORIZED);
                    return;
                }

                String email = jwtTokenProvider.getEmail(token);
                CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(email);
                UsernamePasswordAuthenticationToken auth =
                    new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                SecurityContextHolder.getContext().setAuthentication(auth);
            } catch (TokenExpiredException e) {
                CustomResponseUtil.fail(response, "토큰이 만료되었습니다.", HttpStatus.UNAUTHORIZED);
                return;
            } catch (TokenInvalidException e) {
                CustomResponseUtil.fail(response, "유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED);
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
