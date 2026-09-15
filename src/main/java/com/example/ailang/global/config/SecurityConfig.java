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

    // ⚠️ CORS 설정을 «주입받는다». 종전에는 같은 클래스의 @Bean 메서드를 직접 호출했는데,
    //    그 메서드가 설정값을 인자로 받게 되면서 직접 호출이 불가능해졌다.
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http,
                                           CorsConfigurationSource corsConfigurationSource) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .cors(cors -> cors.configurationSource(corsConfigurationSource))
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

    /**
     * CORS 허용 출처.
     *
     * <p>🔴 주소를 여기 박지 않는다. 종전에는 {@code http://localhost:5173} 이 이 파일과
     * {@code application.yml} 의 OAuth2 리다이렉트 두 곳에 각각 리터럴로 있었다. 배포하며
     * 한쪽만 고치면 <b>로그인은 성공하는데 화면이 응답을 못 받는</b> 상태가 된다 — 증상이
     * 로그인 쪽에 나타나서 원인을 CORS 에서 찾지 않게 되는 종류의 고장이다.
     *
     * <p>정본은 {@code app.frontend.origin} 하나이고, 값은 {@code .env} 에서 온다.
     *
     * <p>⚠️ {@code setAllowCredentials(true)} 이므로 출처에 {@code *} 를 넣을 수 없다.
     * 값이 비면 스프링이 기동 단계에서 실패한다 — 조용히 아무 출처나 허용되는 것보다 낫다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.frontend.origin}") String frontendOrigin) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendOrigin));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
