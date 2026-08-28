# REVIEW 2026-08-27 — plan-eng-review (설계·구현 전체)

> **대상** 브랜치 `docs/project-discipline` · 커밋 `01802c1`
> **범위** `docs/` 다섯 문서(PRD·DATA_CONTRACT·API_CONTRACT·00_CODE_WALKTHROUGH·rules) +
> 구현 전체 (Java 101개 파일, Python 13개 파일, 약 3,700줄)
> **방식** gstack `/plan-eng-review` — 아키텍처 → 코드 품질 → 검사 → 성능 4개 절.
> 코드를 읽고 문서와 맞대는 방식. 🔴 **빌드·실행은 하지 못했다** (§0 참고).
> **코드 수정 없음.** 사용자 지시에 따라 이 문서만 남긴다.

---

## §0. 이 리뷰를 읽기 전에

### 근거 등급 — 지적마다 붙어 있다

| 등급 | 뜻 |
| --- | --- |
| **확인함** | 돌려 봤거나 외부 근거로 재현·확정했다 |
| **코드 대조** | 해당 줄을 읽고 판단했다 (`파일:줄` 함께) |
| ⚠️ **추정** | 라이브러리·프레임워크 동작으로 유추했다 — 실물 확인 필요 |

🔴 **이 환경에는 JDK 21 이 없어 컴파일·테스트를 돌리지 못했다.**
(`~/.gradle/jdks` 에 17만 있고 툴체인 자동 다운로드가 설정돼 있지 않다.)
그래서 「확인함」 등급은 외부 근거로 확정한 **1건뿐**이고, 나머지는 코드 대조다.

### 심각도

| | 뜻 |
| --- | --- |
| 🔴 **P1** | 지금 사용자에게 잘못된 결과가 보이거나, 데이터가 틀리게 저장된다 |
| 🟠 **P2** | 부하·장애·동시성 상황에서 무너진다 |
| 🟡 **P3** | 지금은 안 아프지만 다음 사람이 밟는다 |

### 용어

| 말 | 뜻 |
| --- | --- |
| **직렬화** | 자바 객체를 JSON 으로 바꾸는 것. 이때 필드 이름이 바뀔 수 있다 |
| **트랜잭션** | DB 작업 여러 개를 한 덩어리로 묶는 것. 묶인 동안 DB 연결을 붙잡는다 |
| **커넥션 풀** | 미리 만들어 둔 DB·Redis 연결 묶음. 개수가 정해져 있어 다 쓰면 나머지는 기다린다 |
| **멱등성** | 같은 작업을 두 번 해도 결과가 같은 성질 |

---

## §1. Step 0 — 스코프 도전

### 무엇이 이미 있나 (What already exists)

| 이미 있는 것 | 다시 만들 뻔한 것 | 판정 |
| --- | --- | --- |
| `JwtAuthenticationFilter` 가 매 요청마다 `User` 를 조회해 `CustomUserDetails` 에 담는다 | 컨트롤러 10곳이 `userService.getUserByEmail()` 로 **같은 유저를 또 조회**한다 | 🔴 재사용 안 하고 있다 (F-11) |
| `CustomUserDetails.getUserId()` | 위 재조회를 없앨 수 있는 메서드가 **이미 있는데 호출 0건** | 🔴 죽은 코드 |
| `AuthServiceImpl.issueTokens()` | `OAuth2SuccessHandler` 가 같은 로직을 복사해 갖고 있다 | 🔴 DRY 위반 (F-08) |
| `UserChapterStats.recordAnswer()` | 난이도 규칙이 **한 곳에만** 있다 | ✅ 잘 돼 있다. 흩뜨리지 말 것 |
| `Problem.sourceType` + 쿼리 5개의 `SOURCE_TYPE='REAL'` | 기출/AI 분리 | ✅ 돌아간다. 다만 손으로 적는다 (F-14) |
| `normalizeAnswer` | LaTeX·원문자 흡수 | ✅ 있다. 🔴 검사가 없다 (§4) |
| `scripts/normalize_answers.py` | 저장 데이터 자체 정규화 | ✅ 있다. ⚠️ DB 접속 정보 하드코딩 |

### 남은 일의 규모 (`TODOS.md` 대조)

`TODOS.md` 에 열린 항목이 **8개 절, 약 40건**이다. 이 정도면 「계획」이 아니라 「백로그」다.

🎯 **스코프 판정: 지금 순서가 맞다.** `TODOS.md` 가 이미
`Phase 2.5(채점·인증 경계) → Phase 3(RAG)` 로 못 박아 두었고, 이 리뷰의 결론도 같다.
**RAG 를 먼저 붙이면 코드가 늘고, 늘어난 코드 위에서 같은 결함을 고치는 것이 더 비싸다.**

⚠️ 다만 **1절(채점) 안에서도 순서가 있다.** 아래 F-01 은 다른 무엇보다 먼저다 —
지금 화면에서 학생이 틀린 결과를 보고 있을 수 있기 때문이다.

### 복잡도 검사

- 새 클래스·서비스 추가 없음 (리뷰 대상이 기존 구현이다) → 복잡도 게이트 미발동
- ⚠️ 다만 **어휘가 세 곳에 복사돼 있다** (자바 enum + 파이썬 2곳) — 학년 하나 늘리는 데
  세 파일을 고쳐야 한다. 이건 「파일 수」가 아니라 **결합**의 문제다 (F-14)

### 검색 검사 (Layer 1/2/3)

| 확인한 것 | 결과 |
| --- | --- |
| Jackson 이 `boolean isCorrect` 를 어떻게 직렬화하나 | **확인함** — `is` 를 떼고 `correct` 로 나간다. JavaBeans 규약 (F-01) |
| Spring 에 요청 횟수 제한 내장이 있나 | **[Layer 1]** Spring 자체엔 없다. Bucket4j·Resilience4j 가 표준. 직접 만들지 말 것 |
| `RestTemplate` 타임아웃 | **[Layer 1]** `ClientHttpRequestFactory` 설정이 표준. Spring Boot 4 는 `RestClient` 권장 |

---

## §2. 아키텍처 리뷰 — 지적 6건

### 🔴 F-01 (P1, 확인함 9/10) — 제출 응답의 정답 여부가 다른 이름으로 나간다

`domain/problem/dto/response/SubmitAnswerResponse.java:18`
```java
    private boolean isCorrect;
```

Lombok `@Getter` 가 `isCorrect()` 를 만들고, Jackson 은 boolean 읽기 메서드에서 **`is` 를
떼는** JavaBeans 규약을 따른다. 그래서 실제 JSON 키는 **`"correct"`** 다.

**어떻게 드러나나**: 프론트가 `data.isCorrect` 를 읽으면 `undefined` → 거짓 →
🔴 **정답을 맞혔는데 「틀렸습니다」로 보인다.** `docs/API_CONTRACT.md` §5 도 `isCorrect` 로
적혀 있으니, 문서를 보고 만든 프론트는 반드시 이 상태다.

⚠️ 같은 클래스의 `correctAnswer`·`explanation`·`updatedDifficulty` 는 정상이라,
**「해설은 나오는데 정답 표시만 이상하다」**는 형태로 나타난다 — 원인 찾기가 어렵다.

📌 `UserInfoResponse.assessmentCompleted` 는 **문제없다** — 필드명이 `is` 로 시작하지 않아
`isAssessmentCompleted()` 에서 `is` 를 떼면 원래 이름이 나온다.

**권고**: 필드를 `correct` 로 바꾸거나 `@JsonProperty("isCorrect")` 를 붙인다.
🔴 **어느 쪽이든 프론트와 동시에 고쳐야 한다** — 한쪽만 고치면 지금과 반대로 깨진다.

---

### 🔴 F-02 (P1, 코드 대조 9/10) — 정답 공개 API 에 유형·시점 검사가 없다

`domain/problem/service/ProblemServiceImpl.java:126-129`
```java
    public AnswerRevealResponse revealAnswer(Long problemId) {
        Problem problem = problemRepository.findById(problemId).orElseThrow(ProblemNotFoundException::new);
        return AnswerRevealResponse.of(problem.getAnswer(), problem.getExplanation());
    }
```

`problemType` 도, 「이 학생이 이 문제를 제출했나」도 보지 않는다.
로그인만 하면 **객관식 정답이든 제출 전이든** 받는다.

**🎯 이 리뷰의 구조적 발견**: 두 번째 검사를 **지금 코드로는 못 만든다.**
`UserProblemHistoryRepository` 가 **비어 있다** (조회 메서드 0개).

`domain/problem/repository/UserProblemHistoryRepository.java`
```java
public interface UserProblemHistoryRepository extends JpaRepository<UserProblemHistory, Long> {
}
```

즉 「이 학생이 이 문제를 이미 풀었나」를 물을 방법이 코드에 없다.
`docs/DATA_CONTRACT.md` §1 이 *"이력은 사실, 통계는 요약"* 이라고 적어 두었지만,
**이력을 읽는 경로가 아예 없다.** 쓰기만 하고 읽지 않는 표다.

**권고**: `existsByUserIdAndProblemId` 를 먼저 추가한다. 이게 F-02 의 전제이고,
동시에 「같은 문제 반복 제출로 통계 부풀리기」를 막는 전제이기도 하다.

---

### 🔴 F-03 (P1, 코드 대조 9/10) — 로그아웃이 실패를 삼킨다

`domain/auth/service/AuthServiceImpl.java:106-117`
```java
        if (accessToken != null) {
            try {
                ...
                redisService.save(BLACKLIST_KEY_PREFIX + accessToken, "logout", Duration.ofMillis(remaining));
            } catch (Exception ignored) {}
        }
        cookieUtil.deleteAccessTokenCookie(response);
```

`catch (Exception ignored) {}` — Redis 가 죽어 있으면 **블랙리스트 등록이 조용히 실패**하고,
쿠키만 지워진 채 **200 OK** 가 나간다.

**어떻게 드러나나**: 학생은 로그아웃했다고 믿는다. 그런데 탈취된 토큰은 만료(1시간)까지
그대로 유효하다. 🎯 **이건 커밋 `8c59162`(「로그아웃 후 탈취된 Access Token 유효」)에서
고쳤던 바로 그 취약점이 장애 상황에서 되살아나는 경로다.**

**권고**: 실패를 로그로 남기고, 최소한 「완전히 로그아웃되지 않았다」를 응답으로 구분한다.
전역 핸들러에 `RedisConnectionFailureException → 503` 이 이미 있으니 삼키지만 않으면 된다.

---

### 🟠 F-04 (P2, 코드 대조 8/10) — 한 계정은 한 기기에서만 쓸 수 있다

`AuthServiceImpl.java:127` · `OAuth2SuccessHandler.java:42`
```java
        redisService.save(REFRESH_KEY_PREFIX + email, refreshToken, ...);
```

리프레시 토큰을 **이메일 하나**로 저장한다. 두 번째 기기에서 로그인하면 첫 기기의 토큰이
덮인다. 그 뒤 첫 기기가 `/api/auth/reissue` 를 부르면
`storedToken.equals(refreshToken)` 이 거짓 → `RefreshTokenNotFoundException` → 404.

**어떻게 드러나나**: PC 에서 풀다가 태블릿으로 로그인하면, PC 로 돌아왔을 때 1시간 안에
튕긴다. 🎯 **초·중생 대상 학습 서비스에서 기기 두 개는 예외가 아니라 기본이다.**

⚠️ 의도적 설계일 수도 있다(동시 접속 차단). 그렇다면 **문서에 없다** — `API_CONTRACT.md`
어디에도 「한 기기만」이 적혀 있지 않다. 의도라면 적고, 아니라면 기기별 키로 나눈다.

---

### 🟠 F-05 (P2, 코드 대조 8/10) — 두 서버가 「로그아웃」에 합의하지 않는다

`ai-server/app/services/auth_service.py:13-21` 은 서명과 만료만 본다.
`docker-compose.yml:34` 이 `8001:8000` 으로 호스트에 포트를 연다.

**어떻게 드러나나**: 로그아웃한 토큰으로 `http://localhost:8001/ai/chat` 을 직접 부르면
만료 전까지 Gemini 를 쓸 수 있다. 요금이 나가는 자원이다.

⚠️ 이미 `TODOS.md` 2절에 있다 — **기지(旣知)**. 다만 이 리뷰가 더하는 것:
**가장 싼 해법은 블랙리스트 공유가 아니라 포트를 안 여는 것**이다. Spring 만 부르는
서버이므로 compose 네트워크 안에만 두면 표면이 사라진다.

---

### 🟡 F-06 (P3, ⚠️ 추정 6/10) — 구글 이메일 검증 여부를 안 본다

`global/security/oauth2/GoogleOAuth2UserInfo.java:13`
```java
    @Override public String getEmail() { return (String) attributes.get("email"); }
```

`email_verified` 클레임을 확인하지 않는다. 이 앱은 **이메일로 계정을 잇기** 때문에
(LOCAL↔GOOGLE 차단 판정이 이메일 기준), 검증 안 된 주소가 들어오면 판정이 흔들린다.

⚠️ **추정 이유**: 구글은 일반 계정에서 검증된 이메일만 주는 것이 보통이라 실제 악용
경로가 있는지는 확인 못 했다. Workspace·커스텀 도메인에서 다를 수 있다.
**표준 하드닝 항목이므로 적어 둔다.**

---

## §3. 코드 품질 리뷰 — 지적 8건

### 🔴 F-07 (P1, 코드 대조 9/10) — 인증코드에 시도 횟수 제한이 없다

`domain/auth/service/EmailVerificationServiceImpl.java:42-47`
```java
    public void verifyCode(String email, String code) {
        String storedCode = redisService.get(CODE_KEY_PREFIX + email).orElseThrow(ExpiredCodeException::new);
        if (!storedCode.equals(code)) {
            throw new InvalidCodeException();
        }
```

6자리 숫자 · 5분 유효 · **시도 횟수 제한 없음 · 잠금 없음**.
100만 가지인데 5분 안에 수만 번 시도할 수 있으면 확률이 무시할 수준이 아니다.

**어떻게 드러나나**: 남의 이메일 주소로 인증을 통과해 그 주소로 가입할 수 있다
(선점). `sendCode` 가 이미 가입된 주소는 막으므로 **미가입 주소 선점**이 피해다.

🎯 **묶어서 볼 것**: `sendCode`(`:32-39`)에도 제한이 없다. 같은 주소로 무한히 보낼 수 있고,
**임의의 주소로 메일을 보내는 창구**가 된다. 무료 Gmail 은 일일 발송 한도가 있어
서비스 자체가 멈춘다.

⚠️ `CodeGenerator` 는 `SecureRandom` 을 쓴다 — **이쪽은 문제없다.**

---

### 🟠 F-08 (P2, 코드 대조 9/10) — Redis 키 접두사가 여섯 곳에 흩어져 있다

논리적으로 키는 셋인데 상수는 여섯 개다.

| 키 | 정의된 곳 |
| --- | --- |
| `refresh:token:` | `AuthServiceImpl:42` · `OAuth2SuccessHandler:31` |
| `blacklist:access:` | `AuthServiceImpl:44` · `JwtAuthenticationFilter:32` |
| `email:verified:` | `AuthServiceImpl:41` · `EmailVerificationServiceImpl:28` |

`JwtAuthenticationFilter.java:31` 의 주석이 위험을 **인정하고 넘어간다**:
```java
    // 블랙리스트 키 접두사 (AuthServiceImpl과 동일하게 유지)
```

**어떻게 드러나나**: 한쪽만 바뀌면 **컴파일도 되고 테스트도 통과하는데 로그아웃만 조용히
무력화**된다. F-03 과 겹치면 「로그아웃했는데 안 됐다」가 두 경로로 생긴다.

**권고**: `RedisKeys` 같은 한 자리로 모은다. DRY 이전에 **정확성** 문제다.

---

### 🟠 F-09 (P2, 코드 대조 8/10) — FastAPI 가 요청마다 클라이언트를 새로 만든다

`ai-server/app/routers/chat.py:24` — `rag = RagService()`
`ai-server/app/services/rag_service.py:11-19`
```python
        self.llm = ChatGoogleGenerativeAI(...)
        self.redis = aioredis.Redis(host=..., port=..., decode_responses=True)
```

요청 하나마다 **Redis 커넥션 풀이 새로 생기고, 아무도 닫지 않는다.**
`concept.py:30`·`problem.py:36` 도 같은 모양이다 (이쪽은 LLM 클라이언트만).

**어떻게 드러나나**: 채팅을 쓰면 쓸수록 Redis 연결이 쌓인다. 한도에 닿으면
**AI 기능 전체가 죽는다.** 재시작하면 잠깐 살아나므로 원인 찾기가 어렵다.

**권고**: 모듈 수준 싱글턴이나 FastAPI `lifespan` 으로 한 번만 만든다. **[Layer 1]** —
FastAPI 가 이 자리를 위해 `lifespan` 을 제공한다. 직접 만들 것이 없다.

---

### 🟠 F-10 (P2, 코드 대조 8/10) — 메일 발송이 요청 스레드를 붙잡는다

`global/mail/MailServiceImpl.java:27` — `javaMailSender.send(message)` (동기)
`application.yml` — `connectiontimeout: 5000` · `timeout: 5000` · `writetimeout: 5000`

최악 약 15초 동안 톰캣 스레드 하나가 묶인다. F-07(횟수 제한 없음)과 합치면
**요청 몇 십 개로 스레드 풀을 고갈**시킬 수 있다.

⚠️ 순서도 뒤집혀 있다 (`EmailVerificationServiceImpl:37-38`) — **메일을 먼저 보내고
Redis 에 나중에 저장**한다. Redis 저장이 실패하면 학생은 **절대 통과 못 하는 코드**를 받는다.

---

### 🟡 F-11 (P3, 코드 대조 9/10) — 매 요청 유저를 두 번 조회한다

`JwtAuthenticationFilter:47` 이 이미 `loadUserByUsername(email)` 로 `User` 를 읽어
`CustomUserDetails` 에 담는다. 그런데 컨트롤러 **10곳**이 다시 조회한다.

```java
        User user = userService.getUserByEmail(userDetails.getEmail());
```
(`ProblemController` 7곳 · `UserController` 2곳 · `ChapterController` 1곳)

🎯 **그리고 이걸 없앨 메서드가 이미 있다** — `CustomUserDetails.getUserId():19`,
**호출 0건인 죽은 코드**다.

⚠️ 다만 `getUserId()` 만으로는 `grade` 를 못 얻는다. `CustomUserDetails` 가 `User` 를
통째로 들고 있으므로 접근자를 하나 더 여는 편이 맞다. **성능보다 「두 번 읽는다」는
구조 자체**가 지적 대상이다.

---

### 🟡 F-12 (P3, 코드 대조 8/10) — 한 엔드포인트가 두 가지 일을 한다

`domain/chapter/controller/ChapterController.java:33-50` —
`GET /api/chapters` 가 `grade` 파라미터 유무로 **인증 필요/불필요**가 갈린다.
`SecurityConfig:70` 은 이 경로 전체를 `permitAll` 로 연다.

그래서 **비로그인 + 파라미터 없음** 이면 `userDetails` 가 null 인데 바로 `.getEmail()` 을
부른다 → NPE → 500. `Grade.valueOf(grade)` 도 잘못된 값에 `IllegalArgumentException` 을
던지는데 받는 핸들러가 없다 → 500.

**권고**: 경로를 둘로 나눈다(`/api/chapters` 인증 필요, `/api/chapters/public` 공개).
「명시적 > 영리함」에 맞고, 보안 설정도 경로별로 읽힌다.

---

### 🟡 F-13 (P3, 코드 대조 7/10) — 학년 어휘가 세 곳에 복사돼 있다

`domain/user/enums/Grade.java` (자바) ·
`ai-server/app/services/concept_service.py:8-17` ·
`ai-server/app/services/problem_service.py:11-20` (파이썬 `GRADE_DISPLAY` ×2)

학년을 하나 늘리면 **세 파일**을 고쳐야 한다. 게다가 파이썬은
`GRADE_DISPLAY.get(grade, grade)` 라 **빠져도 예외가 안 난다** — `"HIGH_2"` 가 그대로
프롬프트에 들어가 **조용히 이상한 설명**이 나간다.

---

### 🟡 F-14 (P3, 코드 대조 8/10) — 어휘를 문자열로 손으로 적는다

`ProblemServiceImpl.java:59` — `String difficulty = stats != null ? ... : "MEDIUM";`
`ProblemRepository` — 쿼리 5개 모두 `SOURCE_TYPE = 'REAL'` 을 문자열로
`ChapterService.getMyChapters(Long, String grade)` — 형제 메서드는 `Grade` enum 을 받는데
이쪽만 String 을 받아 안에서 `Grade.valueOf` 를 한다

`docs/DATA_CONTRACT.md` §3 이 *"문자열로 손으로 적지 말 것"* 이라고 적어 두었는데
**코드가 그 규율을 안 지키고 있다.** `"MEDUIM"` 오타는 컴파일을 통과하고 0건을 준다.

---

## §4. 검사 리뷰

### 🔴 지금 상태: 인프라 없이 돌릴 수 있는 검사가 0개다

`src/test/` 에 파일 하나. 그마저 `@SpringBootTest` 라 **Oracle 과 Redis 가 떠 있어야** 돈다.
즉 `./gradlew test` 는 깨끗한 머신에서 **실패한다.**

🎯 **「테스트가 1개」보다 나쁘다** — 0개에 가깝다. 그런데 채점·난이도는 DB 도 네트워크도
필요 없는 **순수 로직**이다. 검사를 못 붙일 이유가 없는 코드다.

### 커버리지 다이어그램

```
CODE PATHS                                          USER FLOWS
[!] ProblemServiceImpl                              [!] 문제 풀이 여정
  ├── normalizeAnswer()                               ├── [GAP][→E2E] 가입→인증→로그인→
  │   ├── [GAP] LaTeX  $\frac{3}{4}$                 │              챕터→문제→제출→개념
  │   ├── [GAP] 원문자 ①  (실제 데이터에 있다)        ├── [GAP] 구글가입(학년없음)→문제요청
  │   ├── [GAP] 복수정답 ①,③                         ├── [GAP] 두 기기 로그인 (F-04)
  │   └── [GAP] \times · ^{n} · _{n}                 └── [GAP] 로그아웃→8001 직접 호출
  ├── submitAnswer()
  │   ├── [GAP] 객관식 정답/오답                     [!] 에러 상태
  │   ├── [GAP] 단답형 selfJudge=null → 오답기록       ├── [GAP] Gemini 429 → 학생은 500
  │   ├── [GAP] 다른 챕터 id 로 제출                   ├── [GAP] Redis 다운 → 로그아웃 성공?
  │   └── [GAP] 동시 제출 (유니크 위반)                ├── [GAP] 비로그인 /api/chapters → 500
  ├── revealAnswer()                                  └── [GAP] 진단문제 20개 미만
  │   └── [GAP] 객관식인데 정답 공개
  └── getAiProblem()                                [!] 응답 계약
      ├── [GAP] options 1000자 초과                    └── [GAP] isCorrect 키 이름 (F-01)
      └── [GAP] 트랜잭션 안 HTTP

[!] UserChapterStats.recordAnswer()                 [!] AI 서버
  ├── [GAP] totalCount 2→3 경계                       ├── [GAP][→EVAL] 개념설명 프롬프트
  ├── [GAP] 정답률 정확히 80 / 50                      ├── [GAP] JSON 펜스 없음/2개/JSON아님
  └── [GAP] getCorrectRate(total=0)                   └── [GAP] Redis 커넥션 누수 (F-09)

[!] Difficulty
  ├── [GAP] LOW.downgrade() → LOW
  └── [GAP] HIGH.upgrade() → HIGH

[!] DataLoader
  └── [GAP] 두 번 실행해도 중복 없음 (한 번 실행으로는 안 보인다)

COVERAGE: 0/28 경로 검사됨 (0%)  |  코드 경로 0/16  |  사용자 흐름 0/12
QUALITY: ★★★:0 ★★:0 ★:1(contextLoads, 인프라 필요)  |  GAPS: 28 (4 E2E, 1 eval)
```

범례: ★★★ 동작+경계+오류 | ★★ 정상 경로 | ★ 존재 확인
[→E2E] 통합 검사 필요 | [→EVAL] LLM 품질 평가 필요

### 🔴 회귀 규칙 해당 없음

이번 브랜치는 문서 정비가 대부분이고 동작을 바꾸지 않았다.
`DataLoader` 의 경로 가드 추가만 동작 변경인데, 「경로 미설정 시 조기 종료」라
기존 동작(경로 없으면 0건)과 결과가 같다. 🔴 **다만 컴파일을 못 돌렸다** (§0).

---

## §5. 성능 리뷰 — 지적 4건

### 🟠 F-15 (P2, 코드 대조 9/10) — 트랜잭션 안에서 수 초짜리 HTTP 호출

`ProblemServiceImpl.java:216-247` — `getAiProblem` 이 `@Transactional` 인데
그 안에서 `aiServerClient.requestAiProblem(...)` 을 부른다.

```
현재:  [트랜잭션 열림] ── Gemini 호출 2~10초 ── 저장 ── [닫힘]
                        ↑ 이 내내 Oracle 커넥션을 붙잡고 있다
```

동시에 10명이 AI 문제를 요청하면 커넥션 10개가 수 초씩 묶이고,
**로그인·문제 조회 같은 무관한 요청까지 대기**한다. 커넥션 풀이 유한하기 때문이다.

⚠️ 기지 — `TODOS.md` 3절. 이 리뷰가 더하는 것: **F-16 과 겹치면 무한대가 된다.**

---

### 🟠 F-16 (P2, 코드 대조 9/10) — 외부 호출에 타임아웃이 없다

`global/config/AppConfig.java:23` — `return new RestTemplate();`

`RestTemplate` 기본 타임아웃은 **무한**이다. AI 서버가 연결은 받고 응답을 안 주면
톰캣 스레드가 영원히 묶인다. **서버가 죽지 않은 채로 멈춘다.**

🎯 **F-15 + F-16 = 최악의 조합**: 무한 대기하는 HTTP 호출이 트랜잭션 안에 있으므로,
**Oracle 커넥션도 무한히 묶인다.** 둘 중 하나만 고쳐도 크게 나아진다.

---

### 🟡 F-17 (P3, 코드 대조 9/10) — 인증 요청마다 같은 유저를 두 번 읽는다

F-11 과 같은 사안의 성능 면. 인증된 요청 하나당 `SELECT * FROM USERS` 가 2회.
지금 규모에선 안 아프지만, **캐싱을 붙이기 전에 없앨 수 있는 쿼리**다.
`@EnableCaching` 이 `AppConfig:13` 에 이미 켜져 있는데 **쓰는 곳이 한 곳도 없다.**

---

### 🟡 F-18 (P3, ⚠️ 추정 6/10) — 진단 테스트가 랜덤 정렬을 세 번 돈다

`ProblemServiceImpl.java:180-182` — `findRandomsByGradeAndDifficulty` 를 3회 호출.
각 호출이 `ORDER BY DBMS_RANDOM.VALUE` 로 **해당 학년·난이도 전체를 정렬**한다.

⚠️ **추정 이유**: 실제 문제 건수를 모른다. 학년당 수백 건이면 무시할 만하고,
수만 건이면 아프다. `docs/research/aihub-json-structure.md` §3-8 이 **건수를 아직
확인 못 했다**고 적어 두었다 — 그 확인이 이 판단의 전제다.

---

## §6. 실패 모드 — 조용히 실패하는가

| 경로 | 실패 방식 | 검사 | 오류 처리 | 학생이 보는 것 | 판정 |
| --- | --- | --- | --- | --- | --- |
| 제출 응답 키 이름 | 정답이 오답으로 표시 | ❌ | ❌ | **틀린 결과** | 🔴 **치명적 조용한 실패** |
| 로그아웃 + Redis 다운 | 토큰이 안 죽음 | ❌ | ❌ (삼킴) | 「로그아웃 성공」 | 🔴 **치명적 조용한 실패** |
| `selfJudge` 누락 | 오답으로 기록 | ❌ | ❌ | 정상 화면 | 🔴 **치명적 조용한 실패** |
| 적재 경로 오설정 | 문제 0건 | ❌ | ⚠️ 경고 로그 | 「문제가 없습니다」 | 🟠 (이번에 가드 추가) |
| 파이썬 학년 미매핑 | 이상한 프롬프트 | ❌ | ❌ | 그럴듯한 오답 | 🔴 **조용한 실패** |
| Redis 커넥션 누수 | 서서히 고갈 | ❌ | ❌ | 어느 날 AI 전체 실패 | 🟠 |
| Gemini 429 | 500 | ❌ | ⚠️ 있으나 뭉갬 | 「서버 오류」 | 🟠 |
| `options` 1000자 초과 | DB 예외 | ❌ | ❌ | 「서버 오류」 | 🟠 |
| AI 서버 무응답 | 스레드 고갈 | ❌ | ❌ | 무한 로딩 | 🟠 |
| 동시 제출 | 제약 위반 | ❌ | ❌ | 「서버 오류」 | 🟡 |

🔴 **치명적 조용한 실패 4건** — 검사도 없고 오류 처리도 없고 **학생에게 아무 표시도 없다.**
이 넷은 「버그가 있다」가 아니라 **「버그가 있는 줄 모른다」**가 문제다.

---

## §7. NOT in scope — 이번에 보지 않은 것

| 안 본 것 | 왜 |
| --- | --- |
| **빌드·테스트 실행** | 🔴 이 환경에 JDK 21 이 없다. 모든 지적이 정적 판단이다 |
| **프론트엔드** | 이 레포에 없다. F-01 의 실제 영향은 프론트를 봐야 확정된다 |
| **실제 데이터 분포** | 문제 건수·정답 표기 분포를 모른다 (F-18 의 전제) |
| **Gemini 응답 품질** | 프롬프트 평가(eval)는 별도 축이다 |
| **Oracle 튜닝·인덱스** | 실제 조회 패턴이 안 나왔다 |
| **RAG 설계** | 코드가 0줄이다. 설계 리뷰는 붙일 때 |
| **관리자 API** | 없다 |
| **배포·CI** | 결정 대기 항목이다 (`docs/PRD.md` §7) |
| **자가채점 존치 여부** | 🔴 **사용자가 정할 일** — 기지, 반려 |
| **snake_case 필드명 / `supabase` 미사용 / `ddl-auto`** | 🔴 기지, 반려 |

### 배포(Distribution) 검사

이 프로젝트는 새 산출물(바이너리·패키지·이미지)을 만들지 않는다. 다만
🔴 **`docker-compose.yml` 에 Spring 이 없다** — 배포 대상이 안 정해졌다는 뜻이고,
`Dockerfile` 이 `--reload`(개발 옵션)로 실행한다. CI/CD 는 아예 없다.
**결정 대기로 이미 기록돼 있다** (`PRD.md` §7).

---

## §8. 병렬 실행 전략

| 작업 묶음 | 건드리는 모듈 | 의존 |
| --- | --- | --- |
| ① 응답 계약 (F-01) | `domain/problem/dto/` + 프론트 | — |
| ② 채점 정합성 (F-02, selfJudge, 소속검증) | `domain/problem/` | ① 과 같은 도메인 |
| ③ 인증 경계 (F-03, F-04, F-05, F-07, F-08) | `domain/auth/`, `global/security/`, `ai-server/` | — |
| ④ AI 호출 (F-15, F-16, F-09) | `global/config/`, `global/client/`, `ai-server/` | ③ 과 `ai-server/` 공유 |
| ⑤ 검사 붙이기 | `src/test/` | ①②③④ 뒤 |

```
Lane A: ① → ②        (순차 — 둘 다 domain/problem/)
Lane B: ③ → ④        (순차 — 둘 다 ai-server/ 를 건드린다)
Lane C: ⑤            (A·B 병합 뒤)

A 와 B 는 병렬 가능. ⚠️ 충돌 주의: 둘 다 `docs/` 를 갱신한다
```

---

## §9. 구현 과제 (Implementation Tasks)

> 🔴 **이 리뷰는 코드를 고치지 않았다.** 아래는 목록일 뿐이다.

- [ ] **T1 (P1, human: ~1h / CC: ~10min)** — problem/dto — 제출 응답의 `isCorrect` 키 이름 확정
  - 근거: §2 F-01
  - 파일: `SubmitAnswerResponse.java`, `docs/API_CONTRACT.md`, 프론트 레포
  - 확인: 실제 응답 JSON 을 찍어 키를 눈으로 본다
- [ ] **T2 (P1, human: ~2h / CC: ~15min)** — problem — 이력 조회 메서드 추가 후 정답 공개 제한
  - 근거: §2 F-02
  - 파일: `UserProblemHistoryRepository.java`, `ProblemServiceImpl.java`
  - 확인: 객관식 문제로 `/answer` 호출 → 거부되나
- [ ] **T3 (P1, human: ~30min / CC: ~5min)** — auth — 로그아웃 실패를 삼키지 않기
  - 근거: §2 F-03
  - 파일: `AuthServiceImpl.java:116`
  - 확인: Redis 를 내리고 로그아웃 → 200 이 아닌 응답
- [ ] **T4 (P1, human: ~3h / CC: ~20min)** — auth — 인증코드 시도·발송 횟수 제한
  - 근거: §3 F-07
  - 파일: `EmailVerificationServiceImpl.java`
  - 확인: 6회 실패 후 잠기나
- [ ] **T5 (P2, human: ~1h / CC: ~10min)** — config/client — `RestTemplate` 타임아웃 + 트랜잭션 분리
  - 근거: §5 F-15, F-16
  - 파일: `AppConfig.java`, `ProblemServiceImpl.java:216`
  - 확인: AI 서버를 멈춰 두고 요청 → 지정 시간에 끊기나
- [ ] **T6 (P2, human: ~1h / CC: ~10min)** — ai-server — 클라이언트를 lifespan 싱글턴으로
  - 근거: §3 F-09
  - 파일: `ai-server/app/main.py`, `services/*.py`, `routers/*.py`
  - 확인: 채팅 100회 후 Redis `CLIENT LIST` 개수
- [ ] **T7 (P2, human: ~1h / CC: ~10min)** — auth — Redis 키 접두사를 한 자리로
  - 근거: §3 F-08
  - 파일: 6개 상수 → `global/redis/RedisKeys.java`
- [ ] **T8 (P2, human: ~1일 / CC: ~30min)** — test — 순수 로직 검사 착수
  - 근거: §4
  - 파일: `src/test/.../normalizeAnswer`, `Difficulty`, `UserChapterStats`
  - 확인: 🔴 **옛 코드에서 빨간불인지 먼저 본다**
- [ ] **T9 (P3, human: ~2h / CC: ~15min)** — chapter — 엔드포인트 분리 + 학년 파싱 예외 처리
  - 근거: §3 F-12
- [ ] **T10 (P3, human: ~2h / CC: ~15min)** — 전역 — 어휘 손타이핑 제거, 유저 이중 조회 제거
  - 근거: §3 F-13, F-14 · §5 F-11, F-17

---

## §10. 결정 대기 — 🔴 혼자 정하지 말 것

| # | 무엇 | 왜 사용자가 정하나 |
| --- | --- | --- |
| D-1 | **F-01 을 어느 쪽으로 맞출 것인가** — 자바를 고치나 프론트를 고치나 | 프론트 레포 사정을 모른다. 🔴 **한쪽만 고치면 지금과 반대로 깨진다** |
| D-2 | **F-04 가 의도인가** — 한 계정 한 기기가 정책인가 | 정책이면 문서에 적고 닫는다. 아니면 기기별 키로 나눈다 |
| D-3 | **F-05 를 포트 닫기로 풀 것인가** | 8001 을 밖에서 쓸 계획이 있는지는 배포 결정에 달렸다 |
| D-4 | **F-07 의 제한 수치** | 「몇 번 틀리면 잠그나」는 학습자 경험 문제다 |

---

## 부록 A — 테스트 플랜 원문

> 🔴 gstack `/plan-eng-review` 가 `~/.gstack/projects/minimini1212-ailang/` 아래에 만든
> 산출물이다. **레포 밖 로컬 캐시라 다른 사람 머신엔 없고 gstack 정리 한 번에 사라진다.**
> 그래서 경로만 적지 않고 원문 그대로 옮긴다
> ([`../README.md`](../README.md) 부록 규율).
>
> 원본: `USER-docs-project-discipline-eng-review-test-plan-20260827-135022.md`
> **이 원문은 고치지 않는다.** 이후 확정으로 달라진 부분은 각주로 아래에 적는다.

```markdown
# Test Plan
Generated by /plan-eng-review on 2026-08-27
Branch: docs/project-discipline
Repo: minimini1212/ailang
Commit: 01802c1

## Affected Pages/Routes

프론트엔드는 이 레포에 없다. API 기준으로 적는다.

- POST /api/problems/{id}/submit — 채점·통계·난이도. 제품의 심장. 응답 키 이름 문제 포함
- GET /api/problems/{id}/answer — 정답 공개. 유형·시점 검사 없음
- GET /api/problems/adaptive|random|random-by-grade|assessment — 문제 선택
- GET /api/chapters — 파라미터 유무로 두 가지 일을 한다. 비로그인 경로 존재
- POST /api/auth/email/send, /email/verify — 횟수 제한 없음
- POST /api/auth/logout — 실패를 삼킨다
- POST /api/ai/chat — 세션 소유권 없음
- FastAPI POST /ai/chat|concept|problem — 요청마다 클라이언트를 새로 만든다

## Key Interactions to Verify

- 정답을 맞혔을 때 화면이 「정답」으로 보이나 — 응답 키가 correct 인데 프론트가
  isCorrect 를 읽고 있으면 정답인데 오답으로 보인다. 가장 먼저 확인할 것
- 객관식 4문제를 연속으로 맞혔을 때 난이도가 MEDIUM → HIGH 로 올라가나
- 3문제 미만일 때 난이도가 안 움직이나
- 단답형에서 selfJudge 를 빼고 제출했을 때 무슨 일이 일어나나 (현재: 오답 기록)
- 한 계정으로 두 기기에서 로그인한 뒤, 먼저 로그인한 기기가 계속 쓸 수 있나
  (현재: 못 쓴다 — 리프레시 토큰이 이메일 하나로만 저장된다)
- 로그아웃한 뒤 그 토큰으로 http://localhost:8001/ai/chat 을 직접 부르면 되나 (현재: 된다)

## Edge Cases

- GET /api/chapters 를 비로그인 + 파라미터 없이 호출 (현재: 500)
- GET /api/chapters?grade=XX 처럼 없는 학년값 (현재: 500)
- 구글로 가입한 계정으로 /api/problems/assessment 호출 (현재: 학년 null → 500)
- 진단 테스트에서 해당 학년 문제가 20개 미만일 때 (현재: 조용히 적은 배열)
- 다른 챕터의 chapterId 를 넣어 제출 (현재: 그 챕터 통계가 오른다)
- 같은 계정으로 답안 두 건을 동시 제출 (현재: 유니크 제약 위반 가능)
- Gemini 할당량 초과 상태에서 개념 설명 요청 (현재: 학생은 500 을 본다)
- AI 모의문제의 보기 4개가 1000자를 넘을 때 (현재: DB 예외 → 500)
- 인증코드를 틀린 값으로 수백 번 시도 (현재: 제한 없음)
- Redis 가 죽은 상태에서 로그아웃 (현재: 성공처럼 보이나 토큰은 살아 있다)

## Critical Paths

1. 가입 → 인증 → 로그인 → 챕터 → 맞춤 문제 → 제출 → 난이도 갱신 → 개념 설명
   이 한 줄이 제품 전체다. 이 경로에 통합 검사 1개가 없는 것이 지금 가장 큰 구멍
2. 구글 로그인 → 학년 없음 → 문제 요청 — 지금 500 으로 끊긴다
3. 로그아웃 → 두 서버 모두에서 무효 — 지금 Spring 만 무효

## 우선 붙일 순수 단위 검사 (인프라 불필요)

| 대상 | 왜 여기부터인가 |
| --- | --- |
| ProblemServiceImpl.normalizeAnswer | 정규식 8줄, 검사 0개. LaTeX·원문자·복수정답이 실제 데이터에 있다 |
| Difficulty.upgrade() / downgrade() | 양 끝 경계(LOW 에서 더 내리기 / HIGH 에서 더 올리기) |
| UserChapterStats.recordAnswer | totalCount 2→3 경계, 80%/50% 경계값, 경로 의존성 |
| UserChapterStats.getCorrectRate | totalCount == 0 |
| AiProblemService.generate 의 JSON 파싱 | 펜스 없음 / 펜스 2개 / JSON 아님 |
```

---

## 부록 B — 바깥 목소리 (Outside Voice) 와 교차 대조

gstack 은 리뷰 뒤 다른 AI 의 독립 의견을 받도록 되어 있다. **Codex 는 이 머신에 없어서**
(`CODEX_MODE: not_installed`) 규정대로 Claude 서브에이전트로 대체했고, 그것이 같은 날 돌린
[`../oh-my-claudecode/REVIEW_2026-08-27_full-codebase.md`](../oh-my-claudecode/REVIEW_2026-08-27_full-codebase.md)
(전수 코드 리뷰, 40건)다.

### 🔴 바깥 목소리가 **이 리뷰가 못 본 것**을 5건 찾았다

| 저쪽 # | 무엇 | 이 리뷰가 왜 놓쳤나 |
| --- | --- | --- |
| 🔴-1 | **정규화가 서로 다른 답을 같게 만든다** (`√2` 와 `2` 가 같아진다) | 나는 `normalizeAnswer` 를 「검사가 없다」로만 적고 **내용을 검증하지 않았다.** 저쪽은 같은 정규식을 파이썬으로 **돌려 봤다** |
| 🔴-2 | **리프레시 토큰이 액세스 토큰으로 그대로 통한다** | 나는 토큰 발급/블랙리스트를 각각 봤지만 **두 토큰이 구분 불가**라는 점을 안 물었다 |
| 🔴-3 | **반복 제출로 정답률 조작** (제출 응답이 정답을 주므로 재생 가능) | 나는 F-02 에서 이력 조회 부재를 찾고도 **공격 경로로 잇지 못했다** |
| 🔴-4 | **챗봇에 학생 신원이 아예 안 간다** | 나는 F-05 에서 챗봇 인증을 봤지만 **Spring 쪽 호출 사슬을 끝까지 안 따라갔다** |
| 🟠-17 | compose 가 **`.env` 전체를 AI 컨테이너에** 넘긴다 | 나는 `.env` 공유를 「Redis 값 충돌」 관점으로만 봤다 |

🎯 **차이의 원인은 방법이다.** 이 리뷰는 「문서와 코드를 맞대는」 방식이라
**문서에 적힌 것 주변**을 잘 봤고, 저쪽은 「코드만 전수로 읽는」 방식이라
**문서에 없는 것**을 잘 봤다. 리뷰어를 폴더로 나눠 두는 이유가 이것이다
([`../README.md`](../README.md)).

### 교차 대조 — 두 리뷰가 갈리는 지점

**CROSS-MODEL TENSION 1 — `normalizeAnswer` 를 뭐라고 부를 것인가**
- 이 리뷰: 「검사가 0개다」 (§4) — **품질 문제**로 다뤘다
- 바깥 목소리: 「**지금 틀리게 채점하고 있다**」 (🔴-1) — **정확성 결함**으로 다뤘다
- 📌 **바깥 목소리가 맞다.** 실행 결과를 냈고 나는 안 냈다.
  ⚠️ 다만 **실제 자료에 LaTeX 정답이 몇 건인지는 양쪽 다 모른다.**
  0건이면 잠재 결함이고, 많으면 이미 사고다. 그 확인이 먼저다

**CROSS-MODEL TENSION 2 — F-04(한 계정 한 기기)**
- 이 리뷰: 🟠 P2 로 올렸다 — 초·중생에게 기기 두 개는 기본이라고 봤다
- 바깥 목소리: **지적하지 않았다** (같은 코드를 읽고도)
- 📌 저쪽은 그것을 **의도된 단일 세션 정책**으로 읽었을 수 있다. 반대로 이 리뷰는
  🔴-2(두 토큰이 구분 불가)를 못 봐서 **같은 코드에서 더 큰 문제를 놓쳤다.**
  🎯 **둘을 합치면**: 세션 설계 전체를 한 번에 정하는 편이 맞다 — 기기 수와 토큰 종류가
  같은 결정이다

**CROSS-MODEL TENSION 3 — FastAPI 의 429 처리**
- `docs/rules/ai-call-policy.md` §5 는 「✅ FastAPI 는 이미 잘하고 있다」로 적혀 있다
  (내가 지난 세션에 쓴 문장이다)
- 바깥 목소리: ⚠️ **아무도 실제 429 를 본 적이 없다.** langchain 이 예외를 감싸면
  `except ResourceExhausted` 가 안 잡힌다 (🟠-14)
- 📌 **✅ 표시를 근거 없이 붙였다.** 재현해 보고 결과가 어느 쪽이든 그 문서를 갱신해야 한다

**합의하는 지점** (양쪽 다 독립적으로 찾음 — 신호가 강하다)
로그아웃 빈 catch · 인증코드 무제한 · FastAPI 커넥션 누수 · 유저 이중 조회 ·
하드코딩 주소 · 테스트 부재.

### 🔴 이 리뷰가 만든 문서 하나가 틀렸다

바깥 목소리 🔴-4 가 **[`../../../TODOS.md`](../../../TODOS.md) 2절의 서술이 틀렸음**을 밝혔다.
거기 적힌 *「키를 `ailang:chat:{user_id}:{session_id}` 로」* 를 그대로 따르면
`user_id` 가 전부 `"service@internal"` 이라 **세션 하이재킹이 그대로 남는다.**

🔴 **「고쳤다」고 믿게 만드는 문서**는 `CLAUDE.md` 가 경계하는 바로 그것이다 —
*"stale text is not harmless — an implementer builds the retired design from it."*
📌 **정정은 사용자 승인 후에 한다** (이번 작업은 파일 수정 금지).

---

## 결정 기록

> 🔴 **이 표의 「결정」 칸은 2026-08-27 리뷰 당일 상태다.**
> 처리 결과는 이 절 아래에 이어 붙인다. 위의 지적 본문은 그날의 기록이므로 고치지 않는다.

| # | 지적 | 심각도 | 근거 등급 | 결정 |
| --- | --- | --- | --- | --- |
| F-01 | 제출 응답 키가 `correct` 로 나간다 | 🔴 P1 | 확인함 9/10 | 🔴 그대로 — D-1 대기 |
| F-02 | 정답 공개에 유형·시점 검사 없음 | 🔴 P1 | 코드 대조 9/10 | 🔴 그대로 |
| F-03 | 로그아웃이 실패를 삼킨다 | 🔴 P1 | 코드 대조 9/10 | 🔴 그대로 |
| F-04 | 한 계정 한 기기 | 🟠 P2 | 코드 대조 8/10 | 🔴 그대로 — D-2 대기 |
| F-05 | 두 서버가 로그아웃에 합의 안 함 | 🟠 P2 | 코드 대조 8/10 | 기지 — D-3 대기 |
| F-06 | 구글 `email_verified` 미확인 | 🟡 P3 | ⚠️ 추정 6/10 | 🔴 그대로 |
| F-07 | 인증코드 시도·발송 제한 없음 | 🔴 P1 | 코드 대조 9/10 | 🔴 그대로 — D-4 대기 |
| F-08 | Redis 키 접두사 6곳 중복 | 🟠 P2 | 코드 대조 9/10 | 🔴 그대로 |
| F-09 | FastAPI 요청마다 커넥션 생성 | 🟠 P2 | 코드 대조 8/10 | 🔴 그대로 |
| F-10 | 메일 동기 발송 + 순서 역전 | 🟠 P2 | 코드 대조 8/10 | 🔴 그대로 |
| F-11 | 매 요청 유저 2회 조회 | 🟡 P3 | 코드 대조 9/10 | 🔴 그대로 |
| F-12 | 한 엔드포인트 두 가지 일 | 🟡 P3 | 코드 대조 8/10 | 🔴 그대로 |
| F-13 | 학년 어휘 3곳 복사 | 🟡 P3 | 코드 대조 7/10 | 🔴 그대로 |
| F-14 | 어휘 문자열 손타이핑 | 🟡 P3 | 코드 대조 8/10 | 🔴 그대로 |
| F-15 | 트랜잭션 안 HTTP 호출 | 🟠 P2 | 코드 대조 9/10 | 기지 |
| F-16 | 외부 호출 타임아웃 없음 | 🟠 P2 | 코드 대조 9/10 | 기지 |
| F-17 | 인증 요청당 쿼리 2회 | 🟡 P3 | 코드 대조 9/10 | 🔴 그대로 |
| F-18 | 진단 테스트 랜덤 정렬 3회 | 🟡 P3 | ⚠️ 추정 6/10 | 🔴 그대로 |

**반려한 지적 (기지 — 이미 정해졌거나 사용자 결정)**

| 지적 | 반려 근거 |
| --- | --- |
| 단답형 자가채점이 취약하다 | [`../../rules/grading-and-difficulty.md`](../../rules/grading-and-difficulty.md) §4 — 세 안과 대가 정리됨, 사용자 결정 |
| `chapter_title` snake_case | [`../../API_CONTRACT.md`](../../API_CONTRACT.md) §7 — FastAPI 계약, 의도됨 |
| `supabase` 미사용 | [`../../PRD.md`](../../PRD.md) §2-⑥ — Phase 3 자리 |
| `ddl-auto: update` | [`../../../TODOS.md`](../../../TODOS.md) 8절 — 마이그레이션 도구는 사용자 결정 |

**남은 위험 (이번에 안 본 것)** — §7 참고. 특히 🔴 **빌드를 못 돌렸다.**

---

## 완료 요약

- Step 0 스코프 도전 — 스코프 유지 (`TODOS.md` 의 Phase 2.5 우선 순서가 맞다)
- 아키텍처 리뷰 — **6건**
- 코드 품질 리뷰 — **8건**
- 검사 리뷰 — 다이어그램 작성, **28개 구멍** (커버리지 0%)
- 성능 리뷰 — **4건**
- NOT in scope — 작성
- What already exists — 작성
- `TODOS.md` 갱신 — 🔴 **안 함** (사용자가 파일 수정 금지를 지시)
- 실패 모드 — **치명적 조용한 실패 4건**
- 병렬화 — 3개 레인 (A·B 병렬, C 순차)

**지적 합계 18건** (🔴 P1 4건 · 🟠 P2 7건 · 🟡 P3 7건)

---

# 결정 기록 — 2026-08-27 (프론트엔드 확인 후)

> 🔴 **위 지적 본문은 그날의 기록이라 고치지 않았다.** 확정 결과는 여기를 본다.
> 계기: 프론트엔드 위치(`C:\webStorm_workspace\ailang`)를 알게 되어 실물을 대조했다.

## 🔴 F-01 — **반려. 이 지적은 틀렸다.**

**확인한 자리**: `src/types/api.types.ts:42~44` · `src/pages/Assessment.tsx:106, 338, 342`

```ts
// 답안 제출 결과 (correct: Java boolean isCorrect → Jackson strips 'is' prefix → 'correct')
export interface SubmitAnswerResult {
  correct: boolean;
```

**메커니즘은 맞았고 영향 판단이 틀렸다.** 프론트는 이미 `correct` 로 받고 있고, **이유까지
주석으로 적어 두었다.** 「지금 학생이 정답을 오답으로 본다」는 이 리뷰의 P1 판정은 사실이
아니었다. 🔴 **오히려 자바를 고치면 멀쩡한 화면이 깨진다.**

📌 **틀린 것은 코드가 아니라 [`../../API_CONTRACT.md`](../../API_CONTRACT.md) 였다** —
옛 `PROJECT_PLANNER.md` 의 `isCorrect` 를 확인 없이 옮긴 것. 그 문서를 정정했다.

🎯 **교훈**: 계약 문제를 「양쪽을 다 보지 않고」 한쪽만 보고 P1 로 올렸다. 프론트가 별도
폴더에 있다는 사실이 §7 「NOT in scope」에 적혀 있었는데, **범위 밖이라는 것과 결론을
낼 수 있다는 것을 혼동했다.**

## 🟠 F-02 — **축소. 권고의 절반이 흐름을 깨뜨린다.**

**확인한 자리**: `src/pages/Assessment.tsx:114~128`(공개) → `:133~145`(제출)

단답형 자가채점은 **공개 → 판단 → 제출** 순서다. 즉 **제출 전 정답 공개가 설계다.**
이 리뷰가 권고한 「이 학생이 이 문제를 이미 제출했는가」 검사를 넣으면 **진단 테스트의
단답형 흐름이 통째로 깨진다.**

✅ **살아남는 절반**: `problemType` 검사. 객관식은 서버가 채점하므로 정답을 미리 줄 이유가
없다. → `SHORT_ANSWER` 일 때만 허용.
❌ **버리는 절반**: 제출 여부 검사.

📌 `UserProblemHistoryRepository` 에 조회 메서드가 없다는 **구조적 지적 자체는 유효하다** —
다만 그 조회가 필요한 곳은 F-02 가 아니라 **반복 제출 차단**(oh-my-claudecode 🔴-3)이다.

## F-04 — **보류. 다른 리뷰의 발견과 묶어서 정한다.**

바깥 목소리 🔴-2(액세스와 리프레시가 구분 불가)와 **같은 결정**이다. 기기 수 정책과
토큰 종류를 따로 정하면 두 번 고치게 된다. → [`../../../TODOS.md`](../../../TODOS.md) 8절

## 나머지 15건 — 🔴 그대로

F-03 · F-05 ~ F-18 은 프론트 확인으로 달라진 것이 없다. `TODOS.md` 에 반영했다.

## 이 리뷰가 놓친 것 (바깥 목소리가 잡음)

🔴-1 정규화 충돌 · 🔴-2 토큰 종류 · 🔴-3 반복 제출 · 🔴-4 챗봇 신원 · 🟠-17 `.env` 전달.
부록 B 참고.

## 이 리뷰가 만든 문서 오류 — 정정 완료

| 문서 | 무엇 | 상태 |
| --- | --- | --- |
| [`../../API_CONTRACT.md`](../../API_CONTRACT.md) §5 | `isCorrect` → `correct` | ✅ 정정 |
| [`../../API_CONTRACT.md`](../../API_CONTRACT.md) §5 | `normalizeAnswer` 서술이 후했다 | ✅ 정정 |
| [`../../../TODOS.md`](../../../TODOS.md) 1절 | 정답 공개에 「시점 검사」 지시 | ✅ 정정 |
| [`../../../TODOS.md`](../../../TODOS.md) 2절 | 챗봇 수정 방법이 틀렸다 | ✅ 정정 |
| [`../../rules/ai-call-policy.md`](../../rules/ai-call-policy.md) §5 | 근거 없는 ✅ | ✅ 정정 |
| [`../../DATA_CONTRACT.md`](../../DATA_CONTRACT.md) | Redis 키 목록 없음 | ✅ §5.5 신설 |
