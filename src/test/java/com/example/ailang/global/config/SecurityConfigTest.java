package com.example.ailang.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithAnonymousUser;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 경로별로 누가 들어올 수 있나.
 *
 * <p>🔴 <b>관리자 경로는 「만들기 전에」 닫아 둔다.</b> {@code /api/admin/**} 엔드포인트는
 * 아직 하나도 없다. 그래서 이 규칙은 <b>오늘 아무것도 막지 않는다</b> — 막는 것은 내일이다.
 * 규칙이 없으면 누군가 관리자 API 를 처음 추가하는 순간 {@code anyRequest().authenticated()}
 * 에 걸려 <b>로그인한 학생 전원에게 열린다.</b> 그때 그 사람이 인가 규칙을 같이 넣는 것을
 * 기억해야 하는데, 기억에 기대는 보호는 결국 한 번은 실패한다.
 *
 * <p>🎯 그래서 이 검사는 <b>엔드포인트가 없는 상태에서도 의미가 있다</b>:
 * 학생은 {@code 403}(권한 없음), 관리자는 {@code 404}(그런 길이 없음)를 받는다.
 * 둘이 <b>다른 코드</b>라는 것이 곧 「인가가 먼저 걸린다」는 증거다.
 *
 * <p>⚠️ 실제 DB 가 필요하다({@code @SpringBootTest}). 없으면 컨텍스트가 안 떠서 실패한다.
 */
@SpringBootTest
@DisplayName("경로별 인가")
class SecurityConfigTest {

    // ⚠️ Spring Boot 4 에는 @AutoConfigureMockMvc 가 이 의존성 묶음에 들어 있지 않다.
    //    검사 하나 때문에 의존성을 더하지 않고 직접 조립한다 — springSecurity() 를 꼭
    //    붙여야 «보안 필터가 낀» MockMvc 가 된다. 안 붙이면 전부 통과해서 검사가 거짓말을 한다.
    @Autowired WebApplicationContext context;

    MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Nested
    @DisplayName("🔴 관리자 경로")
    class AdminPaths {

        @Test
        @DisplayName("로그인한 «학생» 은 403 으로 막힌다")
        @WithMockUser(roles = "USER")
        void 학생은_막힌다() throws Exception {
            // 🔴 잡는 변형: SecurityConfig 에서 /api/admin/** 규칙을 지우는 것.
            //    지우면 anyRequest().authenticated() 만 남아 학생이 «통과» 하고,
            //    응답은 404 가 된다 — 즉 아래 기대값이 깨진다.
            mockMvc.perform(get("/api/admin/chapters"))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("로그인 안 한 사람은 401 로 막힌다")
        @WithAnonymousUser
        void 비로그인은_막힌다() throws Exception {
            mockMvc.perform(get("/api/admin/chapters"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("관리자는 인가를 통과한다 — 길이 아직 없으니 404 다")
        @WithMockUser(roles = "ADMIN")
        void 관리자는_통과한다() throws Exception {
            // 🎯 404 는 「그런 길이 없다」다. 403 이 아니라는 것이 요점 —
            //    인가는 통과했다는 뜻이고, 그래야 나중에 엔드포인트를 붙였을 때
            //    관리자가 실제로 쓸 수 있다.
            mockMvc.perform(get("/api/admin/chapters"))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("🔴 한 경로가 파라미터 유무로 두 가지 일을 한다")
    class ChaptersEndpoint {

        @Test
        @DisplayName("학년을 지정하면 로그인 없이 볼 수 있다")
        @WithAnonymousUser
        void 학년을_주면_열려_있다() throws Exception {
            mockMvc.perform(get("/api/chapters").param("grade", "MIDDLE_1"))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("🔴 학년 «없이» 로그인 없이 부르면 401 이다 — 예전에는 500 이었다")
        @WithAnonymousUser
        void 학년_없이_비로그인이면_401() throws Exception {
            // 🔴 잡는 변형: ChapterController 의 userDetails == null 확인을 지우는 것.
            //    지우면 바로 .getEmail() 을 불러 NPE → 500 이 난다. 학생 입장에서는
            //    「로그인하면 되는 일」인데 「서버가 고장났다」로 보인다.
            //    ⚠️ 이 경로는 permitAll 이라 보안 설정으로는 못 막는다 — 같은 경로가
            //       파라미터 유무로 두 가지 일을 하고, 하나는 정말로 열려 있어야 한다.
            mockMvc.perform(get("/api/chapters"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("🔴 모르는 학년 값은 400 이다 — 예전에는 500 이었다")
        @WithAnonymousUser
        void 모르는_학년은_400() throws Exception {
            // 🔴 잡는 변형: Grade.from 을 Grade.valueOf 로 되돌리는 것.
            //    valueOf 가 던지는 IllegalArgumentException 을 받는 핸들러가 없어
            //    학생 입력 실수가 «서버 내부 에러» 로 나간다.
            mockMvc.perform(get("/api/chapters").param("grade", "중1"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("나머지는 로그인이 필요하다")
    class ProtectedPaths {

        @Test
        @DisplayName("내 정보는 로그인 없이 못 본다")
        @WithAnonymousUser
        void 내_정보는_막힌다() throws Exception {
            mockMvc.perform(get("/api/users/me"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
