package com.example.ailang.global.security.filter;

import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.enums.AuthProvider;
import com.example.ailang.domain.user.enums.UserRole;
import com.example.ailang.domain.user.enums.UserStatus;
import com.example.ailang.global.exception.TokenExpiredException;
import com.example.ailang.global.exception.TokenInvalidException;
import com.example.ailang.global.jwt.JwtTokenProvider;
import com.example.ailang.global.jwt.TokenType;
import com.example.ailang.global.redis.RedisService;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import com.example.ailang.global.security.userdetails.CustomUserDetailsService;
import com.example.ailang.global.security.util.CookieUtil;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 모든 요청이 지나가는 <b>첫 관문</b>.
 *
 * <p>🔴 <b>왜 이 검사가 필요한가.</b> 로그아웃은 이 저장소에서 「블랙리스트에 토큰을
 * 넣는 것」 하나로만 성립한다. 그 확인이 여기 한 줄뿐이라, 그 줄이 사라지거나 순서가
 * 밀리면 <b>로그아웃이 통째로 무력해진다 — 그런데 컴파일도 되고 다른 검사도 전부 통과한다.</b>
 * 증상은 「로그아웃했는데 계속 로그인돼 있다」로 나타나고, 그때는 이미 한참 뒤다.
 *
 * <p>🎯 이 관문이 AI 서버 쪽 로그아웃 문제도 대신 막고 있다. FastAPI 는 Redis 블랙리스트를
 * 모르지만, <b>로그아웃한 학생은 여기서 먼저 막혀</b> AI 를 부르는 컨트롤러까지 가지 못한다.
 * 그래서 「FastAPI 도 로그아웃을 알게 하기」는 구현할 것이 아니라 <b>이 관문이 지켜 주는
 * 성질</b>이다 — 그 성질을 여기서 못 박는다.
 *
 * <p>⚠️ 인프라가 필요 없다. Redis 도 DB 도 가짜다.
 */
@DisplayName("인증 관문 — 쿠키 토큰 검증과 로그아웃")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class JwtAuthenticationFilterTest {

    private static final String TOKEN = "쿠키에-들어-있던-토큰";
    private static final String BLACKLIST_KEY = "blacklist:access:" + TOKEN;

    @Mock JwtTokenProvider jwtTokenProvider;
    @Mock CustomUserDetailsService customUserDetailsService;
    @Mock CookieUtil cookieUtil;
    @Mock RedisService redisService;

    JwtAuthenticationFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    FilterChain chain;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(
                jwtTokenProvider, customUserDetailsService, cookieUtil, redisService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        chain = mock(FilterChain.class);
    }

    @AfterEach
    void tearDown() {
        // 🔴 인증 정보는 스레드에 붙는다. 안 지우면 다음 검사로 새어 나간다.
        SecurityContextHolder.clearContext();
    }

    private void 쿠키에_토큰이_있다() {
        given(cookieUtil.getAccessToken(any())).willReturn(Optional.of(TOKEN));
    }

    private void 쿠키가_비어_있다() {
        given(cookieUtil.getAccessToken(any())).willReturn(Optional.empty());
    }

    private void 학생이_있다(String email) {
        User user = User.builder()
                .email(email).nickname("학생")
                .provider(AuthProvider.LOCAL).status(UserStatus.ACTIVE).role(UserRole.ROLE_USER)
                .build();
        given(customUserDetailsService.loadUserByUsername(email))
                .willReturn(new CustomUserDetails(user));
    }

    @Nested
    @DisplayName("🔴 로그아웃한 토큰")
    class LoggedOut {

        @Test
        @DisplayName("블랙리스트에 있으면 401 이고, «요청이 더 가지 않는다»")
        void 블랙리스트_토큰은_여기서_끊긴다() throws Exception {
            // 🔴 잡는 변형: 블랙리스트 확인 줄을 지우는 것. 지우면 로그아웃이 통째로
            //    무력해지는데 컴파일도 되고 다른 검사도 전부 통과한다.
            쿠키에_토큰이_있다();
            given(redisService.hasKey(BLACKLIST_KEY)).willReturn(true);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("🎯 이 관문이 AI 서버 쪽 로그아웃도 대신 막는다")
        void 로그아웃한_학생은_컨트롤러에_닿지_못한다() throws Exception {
            // FastAPI 는 Redis 블랙리스트를 모른다. 그래도 로그아웃한 학생이 AI 기능을
            // 쓸 수 없는 이유가 «이 줄» 이다 — 컨트롤러에 닿지 못하므로 AI 를 부를 수 없다.
            // 이 검사가 빨간불이면 TODOS 2절의 「FastAPI 도 로그아웃을 알게 하기」가
            // 다시 열린 것이다.
            쿠키에_토큰이_있다();
            given(redisService.hasKey(BLACKLIST_KEY)).willReturn(true);

            filter.doFilter(request, response, chain);

            verify(chain, never()).doFilter(any(), any());
            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .as("로그아웃한 토큰으로 인증이 채워졌다")
                    .isNull();
        }

        @Test
        @DisplayName("블랙리스트 확인이 «인증을 채우기 전» 에 온다")
        void 인증을_채우기_전에_확인한다() throws Exception {
            // 🔴 잡는 변형: 블랙리스트 확인을 인증 설정 «뒤» 로 옮기는 것.
            //    뒤로 가면 그 요청은 이미 인증된 상태로 컨트롤러를 지나간 뒤다.
            쿠키에_토큰이_있다();
            given(redisService.hasKey(BLACKLIST_KEY)).willReturn(true);

            filter.doFilter(request, response, chain);

            verify(customUserDetailsService, never()).loadUserByUsername(anyString());
        }
    }

    @Nested
    @DisplayName("🔴 토큰의 «종류»")
    class TokenKind {

        @Test
        @DisplayName("액세스 토큰만 통과한다 — 리프레시·서비스 토큰은 401")
        void 액세스가_아니면_막힌다() throws Exception {
            // 🔴 잡는 변형: requireType 호출을 지우는 것. 예전에는 서명과 만료만 봐서,
            //    리프레시 토큰을 이 쿠키 자리에 넣으면 모든 API 가 통과했다.
            //    로그아웃은 액세스 토큰만 블랙리스트에 넣으므로, 유출된 리프레시 토큰이
            //    7일 내내 살아 있었다.
            쿠키에_토큰이_있다();
            willThrow(new TokenInvalidException())
                    .given(jwtTokenProvider).requireType(TOKEN, TokenType.ACCESS);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            verify(chain, never()).doFilter(any(), any());
        }

        @Test
        @DisplayName("만료된 토큰은 401")
        void 만료는_401() throws Exception {
            쿠키에_토큰이_있다();
            willThrow(new TokenExpiredException()).given(jwtTokenProvider).validateToken(TOKEN);

            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(401);
            verify(chain, never()).doFilter(any(), any());
        }
    }

    @Nested
    @DisplayName("통과하는 경우")
    class Passing {

        @Test
        @DisplayName("멀쩡한 액세스 토큰이면 인증을 채우고 요청을 넘긴다")
        void 정상_토큰() throws Exception {
            쿠키에_토큰이_있다();
            given(redisService.hasKey(BLACKLIST_KEY)).willReturn(false);
            given(jwtTokenProvider.getEmail(TOKEN)).willReturn("student@example.invalid");
            학생이_있다("student@example.invalid");

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
            assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                    .extracting(Object::toString)
                    .containsExactly("ROLE_USER");
        }

        @Test
        @DisplayName("🎯 토큰이 아예 없으면 «막지 않고» 그냥 넘긴다 — 거부는 다른 곳의 몫이다")
        void 토큰이_없으면_통과시킨다() throws Exception {
            // 로그인 없이 부를 수 있는 길(가입·로그인·챕터 목록)이 있으므로 여기서
            // 막으면 안 된다. 무엇을 열어 둘지는 SecurityConfig 가 정한다.
            쿠키가_비어_있다();

            filter.doFilter(request, response, chain);

            verify(chain).doFilter(any(), any());
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
            verify(redisService, never()).hasKey(anyString());
        }

        @Test
        @DisplayName("토큰이 없으면 Redis 를 부르지 않는다")
        void 토큰이_없으면_Redis_를_안_부른다() throws Exception {
            // 🎯 비로그인 요청마다 Redis 를 때리면 챕터 목록 같은 공개 API 가
            //    저장소 장애에 묶인다.
            쿠키가_비어_있다();

            filter.doFilter(request, response, chain);

            verify(redisService, never()).hasKey(anyString());
            verify(jwtTokenProvider, never()).validateToken(anyString());
        }
    }

    @Nested
    @DisplayName("🔴 블랙리스트 키는 양쪽이 같아야 한다")
    class KeyAgreement {

        @Test
        @DisplayName("로그아웃이 쓰는 키와 «글자까지» 같은 키로 조회한다")
        void 키가_어긋나면_로그아웃이_무력해진다() throws Exception {
            // 🔴 접두사가 이 파일과 AuthServiceImpl 두 곳에 각각 적혀 있다. 한쪽만 바뀌면
            //    **컴파일도 검사도 통과하는데 로그아웃만 조용히 안 먹는다.**
            //    이 검사가 그 어긋남을 잡는다 — 조회 키를 글자 그대로 확인한다.
            쿠키에_토큰이_있다();
            given(redisService.hasKey(anyString())).willReturn(false);
            given(jwtTokenProvider.getEmail(TOKEN)).willReturn("student@example.invalid");
            학생이_있다("student@example.invalid");

            filter.doFilter(request, response, chain);

            verify(redisService).hasKey(eq("blacklist:access:" + TOKEN));
        }
    }
}
