package com.example.ailang.global.security.filter;

import com.example.ailang.global.exception.TokenExpiredException;
import com.example.ailang.global.exception.TokenInvalidException;
import com.example.ailang.global.jwt.JwtTokenProvider;
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

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String token = cookieUtil.getAccessToken(request).orElse(null);

        if (token != null) {
            try {
                jwtTokenProvider.validateToken(token);
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
