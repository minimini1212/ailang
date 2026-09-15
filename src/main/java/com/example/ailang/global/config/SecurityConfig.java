package com.example.ailang.global.config;

import com.example.ailang.global.jwt.JwtTokenProvider;
import com.example.ailang.global.redis.RedisService;
import com.example.ailang.global.security.filter.JwtAuthenticationFilter;
import com.example.ailang.global.security.handler.CustomAccessDeniedHandler;
import com.example.ailang.global.security.handler.CustomAuthenticationEntryPoint;
import com.example.ailang.global.security.handler.OAuth2FailureHandler;
import com.example.ailang.global.security.handler.OAuth2SuccessHandler;
import com.example.ailang.global.security.oauth2.CustomOAuth2UserService;
import com.example.ailang.global.security.userdetails.CustomUserDetailsService;
import com.example.ailang.global.security.util.CookieUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import org.springframework.http.HttpMethod;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtTokenProvider jwtTokenProvider;
    private final CustomUserDetailsService customUserDetailsService;
    private final CookieUtil cookieUtil;
    // 블랙리스트 체크를 위해 JwtAuthenticationFilter에 전달
    private final RedisService redisService;
    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;
    private final CustomAccessDeniedHandler customAccessDeniedHandler;
    private final CustomOAuth2UserService customOAuth2UserService;
    private final OAuth2SuccessHandler oAuth2SuccessHandler;
    private final OAuth2FailureHandler oAuth2FailureHandler;

    /**
     * CORS 를 허용할 프론트 주소. 쉼표로 여러 개.
     *
     * <p>🔴 예전에는 여기에 {@code "http://localhost:5173"} 이 문자열로 박혀 있었고,
     * 같은 주소가 {@code application.yml} 의 로그인 후 리다이렉트 주소에도 따로 있었다.
     * 배포하면서 한 쪽만 고치면 <b>CORS 는 새 주소를 열어 주는데 로그인은 옛 주소로
     * 돌려보내는</b> 상태가 되고, 증상은 「로그인이 되긴 하는데 화면이 안 바뀐다」로 나와
     * 원인을 찾기 어렵다. 값을 하나로 합쳤다 — {@code app.frontend.origin}.
     */
    @Value("${app.frontend.origin}")
    private String frontendOrigin;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**", "/oauth2/**", "/login/oauth2/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/chapters").permitAll()
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth2 -> oauth2
                .userInfoEndpoint(userInfo -> userInfo.userService(customOAuth2UserService))
                .successHandler(oAuth2SuccessHandler)
                .failureHandler(oAuth2FailureHandler)
            )
            .exceptionHandling(exception -> exception
                .authenticationEntryPoint(customAuthenticationEntryPoint)
                .accessDeniedHandler(customAccessDeniedHandler)
            )
            .addFilterBefore(jwtAuthenticationFilter(), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter() {
        // RedisService 추가: 블랙리스트 토큰 검증에 사용
        return new JwtAuthenticationFilter(jwtTokenProvider, customUserDetailsService, cookieUtil, redisService);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(
            Arrays.stream(frontendOrigin.split(","))
                  .map(String::trim)
                  .filter(o -> !o.isEmpty())
                  .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
