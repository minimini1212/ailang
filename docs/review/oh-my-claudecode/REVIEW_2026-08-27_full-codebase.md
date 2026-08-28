# REVIEW 2026-08-27 — code-reviewer (전수 코드 리뷰)

> **대상** 브랜치 `docs/project-discipline` · 커밋 `01802c1` · **작업 트리 상태**
> (미커밋 수정 9건 + 미추적 문서 다수 포함)
> **실측 규모** Java **87개 / 3,247줄** · Python **13개 / 404줄**
> ⚠️ 리뷰 의뢰서에 적힌 「Java 101개 · 3,700줄」은 **틀린 수치**였다 —
> 101 은 Java+Python+스크립트 합계다. 리뷰어가 세어 정정했다.
> **방식** oh-my-claudecode `code-reviewer` 에이전트. 파일을 전부 열어 읽음.
> 빌드·실행은 **못 했다** (JDK 없음). 단 한 건, `normalizeAnswer` 의 정규식만
> **같은 정규식을 파이썬으로 돌려 결과를 재현**했다.
> **코드 수정 없음.**

---

## §0. 이 리뷰를 읽기 전에

### 근거 등급

| 등급 | 뜻 | 이 리뷰에서 |
| --- | --- | --- |
| **확인함** | 실행·재현했다 | 8건 |
| **코드 대조** | 줄을 읽고 판단했다 (`파일:줄` 동반) | 다수 |
| ⚠️ **추정** | 라이브러리·프레임워크 동작으로 유추 | **9건 — 실물 확인 전까지 확정 아님** |

🔴 **추정 9건**: #9 · #11 · #14 · #16 · #21 · #22 · #27 · #36 · #37 · #38

### 심각도

🔴 치명 = 제품의 핵심 고리(정답률→난이도) 또는 인증 경계를 깬다
🟠 중대 = 부하·장애·배포 상황에서 무너진다
🟡 경미 = 지금은 안 아프지만 다음 사람이 밟는다

### 용어

| 말 | 뜻 |
| --- | --- |
| **정답 정규화** | 학생이 낸 답과 저장된 정답을 비교하기 전에 표기 차이를 없애는 처리 |
| **액세스 / 리프레시 토큰** | 매 요청에 쓰는 짧은 자격증명 / 그것을 다시 받아오는 긴 자격증명 |
| **블랙리스트** | 로그아웃한 토큰을 만료 전까지 막아 두는 목록 |
| **클레임** | 토큰 안에 담긴 정보 조각 (누구인가, 언제 만료인가 등) |

---

## §1. 🔴 치명 — 5건

### 🔴-1 정답 정규화가 서로 다른 답을 같은 문자열로 만든다 — **틀린 답이 정답으로 채점된다**

**`ProblemServiceImpl.java:146~163`**(정의) · **`:100~102`**(사용) · **근거: 확인함**

정규화 끝에서 두 번째 줄이 남은 LaTeX 명령어를 **지우기만 하고 자리를 안 남긴다**:

```java
s = s.replaceAll("\\\\[a-zA-Z]+", "");   // :160  나머지 LaTeX 명령어 제거
s = s.replaceAll("[{}]", "");            // :161  중괄호 제거
```

**같은 정규식을 실제로 돌린 결과:**

| 넣은 값 | 정규화 결과 |
| --- | --- |
| `$\sqrt{2}$` | `2` |
| `2` | `2` |
| `$\pi$` | (빈 문자열) |
| `$\alpha$` | (빈 문자열) |
| `$` | (빈 문자열) |
| `2\sqrt{2}` | `22` |
| `$\frac{\sqrt{2}}{2}$` | `22` |
| `\log{x}` | `x` |

**어떻게 드러나나** — 이 함수는 **객관식 채점의 유일한 판정자**다(`:100~102`).
자가채점 문제를 다 고쳐도 이 경로는 남는다.

- `√2` 가 정답인 문제에 학생이 `2` 를 내면 **정답 처리**된다. 다른 수다.
- `2√2` 와 `√2/2` (서로 역수)가 **같은 답**이 된다.
- 정답이 `$\pi$` 같은 순수 명령 하나면 정규화 결과가 빈 문자열이라,
  학생이 **`"$"` 한 글자만 내도 정답**이다 (`@NotBlank` 는 `"$"` 를 통과시킨다 —
  `SubmitAnswerRequest.java:20`).

🔴 [`../../API_CONTRACT.md`](../../API_CONTRACT.md):184 는 이 함수를 「LaTeX·원문자·공백 흡수」라고
**잘 도는 것처럼** 적어 두었다. **문서가 코드보다 후하게 쓰여 있다.**

**권고**: 모르는 명령어를 만나면 지우지 말고 **「비교 불가」를 돌려주는** 쪽이 안전하다.
「모르는 표기를 지워서 억지로 비교 가능하게 만드는 것」이 뿌리다.
🎯 위 8줄을 그대로 픽스처로 쓰면 새 검사가 옛 코드에서 확실히 빨간불이 된다.

---

### 🔴-2 리프레시 토큰이 **액세스 토큰으로 그대로 통한다** — 로그아웃이 막지 못한다

**`JwtTokenProvider.java:25~41`** · **`JwtAuthenticationFilter.java:38~54`** ·
**`AuthServiceImpl.java:104~121`** · **근거: 코드 대조**

두 토큰이 **만료 시간만 다르고 나머지가 전부 같다.**

```java
public String createAccessToken(String email)  { return createToken(email, ...accessExpiration); }   // :25
public String createRefreshToken(String email) { return createToken(email, ...refreshExpiration); }  // :29
// createToken 은 subject·서명키·클레임이 동일 — 종류를 구분하는 claim 이 없다 (:33~41)
```

인증 필터는 **서명과 만료만** 본다(`:42`). 「이게 액세스 토큰인가」를 묻는 자리가 없다.

**어떻게 드러나나**

1. 리프레시 토큰 값을 `accessToken` 쿠키 자리에 넣으면 **모든 API 가 인증된다.**
   즉 리프레시 토큰은 **유효기간 7일짜리 전체 API 자격증명**이다.
2. 로그아웃은 **액세스 토큰만** 블랙리스트에 넣는다(`:114`). 리프레시는
   `refresh:token:{email}` 에서 지워질 뿐이고, **필터는 그 목록을 보지 않는다.**
3. 결과: **유출된 리프레시 토큰은 로그아웃 이후에도 7일간 모든 API 를 부른다.**

⚠️ [`../../rules/ai-call-policy.md`](../../rules/ai-call-policy.md) §4 는 *서비스 토큰과 학생 토큰이
구분 안 된다*는 점을 적었지만, **액세스와 리프레시가 구분 안 된다**는 이 문제는 어디에도 없다.

**권고**: `typ: access` / `typ: refresh` 클레임을 넣고 **필터는 access 만, `reissue` 는
refresh 만** 받게 한다. 하나로 세 갈래가 동시에 닫힌다. 서비스 토큰 문제(기지)와
**같은 뿌리** — 토큰에 「무엇을 위한 토큰인가」가 없다 — 이므로 함께 정하는 편이 낫다.

---

### 🔴-3 **같은 문제를 반복 제출**해 정답률을 원하는 값으로 만들 수 있다 — 객관식도

**`ProblemServiceImpl.java:82~124`** · **`UserProblemHistoryRepository.java:9`** ·
**`SubmitAnswerResponse.java:32~41`** · **근거: 코드 대조**

`submitAnswer` 는 「이 학생이 이 문제를 이미 냈는가」를 **전혀 보지 않는다.**
`USER_PROBLEM_HISTORY` 에 `(user_id, problem_id)` 유니크 제약도 없고, 레포지토리에
조회 메서드도 없다. 동시에 **제출 응답은 언제나 정답을 함께 돌려준다**(`:123`).

**둘의 조합이 문제다. 자가채점을 하나도 안 건드리고 객관식만으로:**

```
① 문제 42 에 "1" 제출  →  {isCorrect:false, correctAnswer:"3"}   ← 정답을 알려준다
② 문제 42 에 "3" 제출  →  {isCorrect:true}                        ← 1회
③ ②를 50번 반복        →  totalCount 51, correctCount 50 = 98%   ← 난이도 HIGH
```

🔴 [`../../rules/grading-and-difficulty.md`](../../rules/grading-and-difficulty.md) §3 이
「1~4 는 한 덩어리다」로 묶은 네 건을 **전부 고쳐도 정답률은 여전히 학생이 정한다.**
이 경로는 그 네 건 어디에도 없다.

**권고**: 난이도 반영을 **문제당 첫 제출 한 번**으로 한정해야 고리가 닫힌다.
이력은 계속 쌓되 통계에만 반영하지 않으면 R6 과 어긋나지 않는다.
🎯 「제출 이력이 있는가」 조회는 정답 공개 API 의 시점 검사에도 그대로 필요하므로,
**두 항목이 조회 하나를 공유한다.**

---

### 🔴-4 챗봇: Spring 이 **학생 신원을 아예 보내지 않는다** — TODOS 에 적힌 수정이 동작하지 않는다

**`AiChatController.java:26~32`** · **`AiServerClient.java:30~36, 112~117`** ·
**`ai-server/app/routers/chat.py:19~29`** · **근거: 코드 대조**

🔴 **이 리뷰에서 가장 값이 큰 발견이다.**
[`../../../TODOS.md`](../../../TODOS.md) 2절은 *「키를 `ailang:chat:{user_id}:{session_id}` 로」*
고치면 된다고 적혀 있다. 그런데 `user_id` 의 출처를 끝까지 따라가면 **학생이 아니다.**

```
AiChatController.chat(...)          ← @AuthenticationPrincipal 이 없다. 유저를 안 받는다 (:26~32)
   → AiServerClient.requestChat(question, sessionId)
   → withAuth(...) : createAccessToken("service@internal")   ← 토큰 subject 가 고정 문자열
   → ChatRequest = { question, session_id }                  ← 유저 식별자 없음 (:112~117)
   → FastAPI chat.py:22  user_id = Depends(verify_token)     ← payload["sub"] = "service@internal"
```

**어떻게 드러나나** — FastAPI 의 `user_id` 는 **모든 학생에게 항상 `"service@internal"`** 이다.
계획대로 키를 바꾸면 전부 `ailang:chat:service@internal:{session_id}` 가 되어
**세션 하이재킹이 그대로 남는다.** 🔴 **「고쳤다」고 믿게 되는 자리다.**

⚠️ `chat.py:22` 의 변수명 `user_id` 가 실제로는 「호출한 서비스의 이름」이라는 것도
이 오해를 키운다.

**권고**: FastAPI 만 고쳐서는 안 닫힌다. **Spring 의 `AiChatController` 가
`@AuthenticationPrincipal` 로 학생을 받아 `ChatRequest` 에 실어 보내는 것**이 먼저다.

---

### 🔴-5 리프레시 토큰 원문과 블랙리스트가 **무인증 Redis** 에 있고, 그 포트가 호스트에 열려 있다

**`docker-compose.yml:15~24`** · **`AuthServiceImpl.java:41~44, 114, 127~128`** ·
**근거: 코드 대조** (비밀번호를 안 건다는 사실 자체는 **기지** — `.env.example` ⑤)

기지인 것은 「비밀번호를 안 건다」까지다. **그 안에 무엇이 들어 있는지**는 어느 문서에도 없다.

```
refresh:token:{email}     ← 리프레시 토큰 원문. 키가 이메일이라 완전히 예측 가능
blacklist:access:{토큰}    ← 로그아웃을 성립시키는 유일한 근거
email:verified:{email}    ← 이걸 넣으면 이메일 인증을 건너뛰고 가입된다
```

compose 는 `ports: - "6380:6379"` 로 **호스트의 모든 인터페이스에** 바인딩한다. 인증 없음.

**어떻게 드러나나** — Redis 에 닿는 누구나 ① 전 학생의 리프레시 토큰을 읽고
(🔴-2 와 합치면 **전체 API 접근**), ② 블랙리스트를 지워 **로그아웃을 되돌리고**,
③ `email:verified:*` 를 넣어 **이메일 인증 없이 가입**할 수 있다.

**권고**: 값의 문제가 아니라 **저장물의 민감도가 어디에도 안 적혀 있다**는 것이 문제다.
[`../../DATA_CONTRACT.md`](../../DATA_CONTRACT.md) 에 Oracle 4개 표만 있고 **Redis 키 목록이 없다.**

---

## §2. 🟠 중대 — 18건

| # | 무엇 | 파일:줄 | 근거 | 요지 |
| --- | --- | --- | --- | --- |
| 6 | **`UserStatus.SUSPENDED` 가 아무 일도 안 한다** | `CustomUserDetails.java:32~35` · `AuthServiceImpl.java:72~80` | 코드 대조 | `status` 를 읽는 코드가 레포에 **없다**. `isEnabled()`·`isAccountNonLocked()` 가 무조건 `true`. 정지된 계정이 그대로 로그인·문제 풀이·챗봇을 쓴다. `DATA_CONTRACT.md:66` 은 실제 어휘처럼 적어 두었다 |
| 7 | 로그아웃이 **실패해도 성공이라 답한다** | `AuthServiceImpl.java:106~117` | 코드 대조 | 레포 유일의 빈 catch(`:116`). 그 블록 안이 **로그아웃의 전부**다. Redis 가 죽으면 둘 다 안 되고 쿠키만 지워진 채 200 |
| 8 | 이메일 인증 세 갈래 | `EmailVerificationServiceImpl.java:31~51` · `SecurityConfig.java:54` | 코드 대조 | ① `/email/send` 가 `permitAll` + 무제한 → 임의 주소로 우리 Gmail 무한 발송 ② `verifyCode` 시도 제한 없음 (TTL 300초가 방어의 전부) ③ 가입 여부가 409 로 새어 나감 |
| 9 | **필터 예외가 전역 핸들러를 못 탄다** | `JwtAuthenticationFilter.java:50~61` · `CustomUserDetailsService.java:22` | 코드 대조 + ⚠️추정 | 필터가 잡는 건 만료·무효 둘뿐인데 그 사이에서 DB 를 본다(`:51`). `UsernameNotFoundException` 은 `@RestControllerAdvice` 밖이다. **탈퇴한 유저 토큰이나 `service@internal` 토큰이 401 이 아니라 500**, 응답 형식도 `ResponseDTO` 가 아니게 된다. 🎯 확인 쉬움: 로그인 후 유저 행을 지우고 아무 API 호출 |
| 10 | AI 호출에 **사용량 제한이 하나도 없다** | `ProblemController.java:104~111` · `ProblemServiceImpl.java:207~236` | 코드 대조 | `ai-generated` 를 반복 호출하면 매번 Gemini 호출 + `PROBLEMS` 에 새 행. 지우지도 세지도 않는다. `ai-call-policy.md` §2 는 **호출 횟수를 안 다룬다** |
| 11 | `Map.of` null 조회가 NPE 라 **바로 아래 가드에 도달 못 한다** | `DataLoader.java:47~59, 109~114, 130~131` | 코드 대조 + ⚠️추정 | `Map.of` 불변 맵은 **null 키 조회 자체가 NPE**. `question_grade` 필드가 없으면 「알 수 없는 학년 코드」 경고 대신 바깥 catch 로 떨어져 `"{id} 처리 실패: null"` 한 줄만 남는다. 🔴 「실패는 종류를 남긴다」가 여기서 무너진다 |
| 12 | 난이도 정보가 없으면 **조용히 `LOW`** | `DataLoader.java:130~131, 223~227, 248` | 코드 대조 | `int questionDifficulty` 는 필드가 없으면 Jackson 이 **0** 으로 둔다 → `difficultyFromScore(0)` = `LOW`. 경고 없이 「하」로 적재되고 그대로 고리에 들어간다 |
| 13 | FastAPI 가 **모르는 난이도를 「중간」으로 바꿔치기** | `problem_service.py:41~42` · `ProblemServiceImpl.java:224` | 코드 대조 | 붙어 있는 두 줄의 실패 방식이 정반대. `ai-call-policy.md` §5 는 41행만 지적하고 **42행이 더 나쁘다** — 프롬프트는 「중간」을 만들고 Spring 은 **요청 난이도로 저장**한다. DB 난이도와 실제 수준이 어긋나고 기록도 안 남는다 |
| 14 | Gemini 예외를 `ResourceExhausted` 하나로만 잡고 **타임아웃·재시도 미설정** | `rag_service.py:11~14,41~44` 외 2곳 | 코드 대조 + ⚠️추정 | ⚠️ langchain 이 예외를 감싸면 `except` 가 안 잡혀 429 대신 500. 🔴 **`ai-call-policy.md` §5 가 「✅ 잘하고 있다」고 단정했지만 아무도 실제 429 를 본 적이 없다.** `PermissionDenied`·`InvalidArgument`·`DeadlineExceeded` 는 전부 500 한 덩어리 — **Spring 에 닿기 전에** 합쳐진다 |
| 15 | 챗봇 이력 **무한 증가** + **시스템 프롬프트 없음** | `rag_service.py:21~34, 36~47` | 코드 대조 | `ltrim` 없이 1시간 누적, 매 턴 전량 재전송 → 비용 누적·컨텍스트 초과. 그리고 **시스템 프롬프트가 아예 없다** — 다른 두 서비스는 「당신은 수학 전문 강사입니다」로 시작하는데 챗봇만 학생 문장을 그대로 넘긴다. **초3 학생이 쓰는 범용 LLM** 이 된다. `expire` 가 별개 await 라 사이에서 죽으면 TTL 없는 키가 영구히 남는다 |
| 16 | 요청마다 클라이언트·커넥션 풀 **새로 만들고 안 닫는다** | `chat.py:24` · `rag_service.py:10~19` | 코드 대조 + ⚠️추정 | `aclose()` 도 `lifespan` 도 없다 |
| 17 | compose 가 **`.env` 전체를 AI 컨테이너에** 넘긴다 | `docker-compose.yml:31~32` | 코드 대조 | AI 서버가 읽는 건 4개뿐인데(`config.py:6~17`), 컨테이너 환경변수에 **Oracle 비밀번호·구글 OAuth 시크릿·Gmail 앱 비밀번호**가 전부 들어 있다. 🔴 **`.env.example` 이 키마다 「Spring 전용」이라 선언했는데 compose 가 그 선언을 지운다** |
| 18 | 커밋된 파일에 **자격증명 하드코딩** | `docker-compose.yml:5~6` · `scripts/normalize_answers.py:18~20` | 코드 대조 | `ORACLE_PASSWORD: oracle`, `DB_USER="ailang"`. `CLAUDE.md` HARD RULE 위반. ⚠️ 리뷰어 스스로 「의도된 개발용 고정값일 수 있다」고 단서를 달았다 — 그렇다면 **이유를 한 줄 적어 두면** 다음 리뷰가 반복하지 않는다 |
| 19 | 정답 정규화가 **두 곳에 서로 다른 내용으로** 산다 | `ProblemServiceImpl.java:146~163` · `normalize_answers.py:26~51` | 코드 대조 | 원문자·괄호형·중복·LaTeX 처리가 전부 다르다. 스크립트는 **백업·dry-run·확인 없이 원본을 덮어쓴다**(`:75~84`). 게다가 **재실행하면 이미 정규화된 값이 전부 `failed` 로 분류**되어 「대량 실패」로 보인다. R5(규칙은 한 곳에)가 정규화엔 적용 안 돼 있다 |
| 20 | 풀이 이력에 **챕터가 없다** | `UserProblemHistory.java:21~44` · `ProblemServiceImpl.java:105~120` | 코드 대조 | 이력은 `problem.chapter` 에, 통계는 요청 바디의 `chapterId` 에 붙는다. 🔴 **「이 제출이 어느 챕터 통계를 움직였나」를 이력만 보고 알 수 없다.** R6 이 통계 재생성 불가 이유로 경로 의존성만 들었는데, **경로 의존성을 풀어도 대상 챕터를 복원할 수 없다** |
| 21 | 배포하면 **쿠키에 `Secure` 가 안 붙을 공산이 크다** | `CookieUtil.java:19~20, 44~57` | 코드 대조 + ⚠️추정 | `application-prod.yml` 이 **없다** → `prod` 분기는 오늘 도달 불가. 프로파일을 둘 켜면 `"prod,aws"` 라 `equals` 가 깨지고, 이름이 `production` 이어도 깨진다. 어느 경우든 `else` 로 가서 **`Secure` 없이 JWT 쿠키**가 나간다. 🔴-2 와 곱해진다. **실패가 조용하다** |
| 22 | OAuth2 실패 처리 **NPE 가능** + 내부 문구 노출 | `OAuth2FailureHandler.java:23~24` | 코드 대조 + ⚠️추정 | `getMessage()` 가 null 이면 `URLEncoder.encode` 가 NPE → 리다이렉트 대신 500, 학생은 흰 화면. `OAuth2AuthenticationException(String errorCode)` 생성자라 실제 메시지가 `[...]` 형태일 가능성 |
| 23 | 하드코딩 주소가 **문서가 센 것보다 두 곳 더** | `application.yml:6, 27, 50` | 코드 대조 | `:6` DB 주소(계정·비번은 환경변수인데 **주소만 리터럴**) · `:27` `smtp.gmail.com` · `:50` **세 번째 오리진** `localhost:8080/login/oauth2/code/google` — `.env.example` ⑧ 이 안 셌다. 배포하면 반드시 바뀐다 |

---

## §3. 🟡 경미 — 17건 (요약)

| # | 무엇 | 요지 |
| --- | --- | --- |
| 24 | 요청마다 `USERS` 2회 조회 | `getUserId()` 는 **호출처 0곳**. `completeAssessment` 는 **3회** 읽는다 |
| 25 | `@EnableCaching` 인데 `@Cacheable` **0개** | 🎯 `supabase` 미사용과 **정확히 같은 함정**이 자바 쪽에도 있다 |
| 26 | 안 쓰는 의존성 | `spring-boot-testcontainers`, `snakeyaml:2.3` 수동 고정 |
| 27 | Phase 4 함정 — 403 이 500 이 될 것 | `@PreAuthorize` 를 서비스에 붙이면 예외가 컨트롤러 안에서 나와 `RuntimeException` 분기에 먼저 걸린다 ⚠️추정 |
| 28 | `DataLoader` 가 파일마다 **두 번 파싱** | `id` 필드가 이미 있는데(`:233,:260`) 아무도 안 읽고 `Map.class` 로 재파싱. 1,340건이면 2,680번 |
| 29 | 챕터 순서가 **실행 환경마다 다르다** | `orderNum` 이 「먼저 만난 파일」로 정해지는데 `listFiles()` 는 OS 의존. `(grade,title)` 유니크 제약도 없다 |
| 30 | 없는 클래스를 가리키는 주석 | `Chapter.java:12` 의 `LearningContent` 는 **레포에 없다** |
| 31 | 컨트롤러 주석이 **7개 중 4개만** | `PROJECT_PLANNER.md` 가 머지된 걸 언급 안 했던 것과 같은 형태 |
| 32 | 죽은 코드 | `okWithData(data,message)` · `findByChapterId()` · 🎯 **`User.updateGrade()`** — 학년 null 문제의 해답이 이미 있는데 아무도 안 부른다 |
| 33 | 유일한 테스트가 **실인프라 필수** | Oracle·Redis·JWT_SECRET·구글 키가 전부 있어야 통과. 「테스트 1개」의 실제 값이 더 낮다 |
| 34 | `ai-server` 에 `.dockerignore` 없음 | `COPY . .` 가 폴더 전부를 굽는다. 누가 `ai-server/.env` 를 만들면 그대로 들어간다 |
| 35 | 노후 의존성 | jjwt `0.11.2`(2020) · `python-jose 3.3.0`(2021, 유지보수 중단) · `class Config:` 는 pydantic-settings v2 에서 대체됨 |
| 36 | `FETCH FIRST :limit` 바인드 | ⚠️추정 — 아마 돌지만 확인된 적 없고, 실패해도 빈 배열이라 눈에 안 띈다 |
| 37 | 시퀀스 `allocationSize=50` + `ddl-auto` | ⚠️추정 — 누가 손으로 `INCREMENT BY 1` 시퀀스를 만들어 두면 번호가 겹친다 |
| 38 | `response.content` 가 문자열이 아닐 수 있다 | 멀티모달 응답에서 리스트 → `.strip()` AttributeError ⚠️추정 |
| 39 | 복수정답이 **순서 의존** | `①,③`→`1,3` / `③,①`→`3,1` **확인함** |
| 40 | 난이도가 **양 끝에서 사실상 안 돌아온다** | 🎯 숫자를 붙이면: ≥80% 면 **제출할 때마다** 상향돼 3·4번째에 `HIGH` 도달. 100문제 85% 학생이 `LOW` 로 오려면 **연속 오답 약 70건** 필요. 「고리」가 20문제쯤부터 사실상 열려 있다 |

---

## §4. ✅ 잘 되어 있는 것 — 다음 사람이 무너뜨리지 말 것

- **`.env` 가 git 에 없다** (`git ls-files` 로 미추적 확인). HARD RULE 이 지켜지고 있다
- 🎯 **문서가 코드를 정확히 반영한다** — `00_CODE_WALKTHROUGH.md` 가 언급한 파일 12개가
  **전부 실재**하고 경로도 맞다. `API_CONTRACT.md` 는 비로그인 `/api/chapters` 의 500,
  `Grade.valueOf` 의 500, `/assessment` 가 20개 미만을 주는 것, `myStats` 의 `0.0` 이
  「안 풀었다」라는 것까지 **코드보다 먼저 적어 두었다.**
  📌 **리뷰어가 「새 지적」으로 가져왔다가 문서를 보고 기지로 내린 항목이 8건이다.**
- **난이도 규칙이 진짜로 한 곳에만 있다** — 밖에서 정답률로 분기하는 코드가 **없다**(grep). R5 준수
- **`REAL` 필터가 조회 쿼리 5개 전부에 실제로 걸려 있다**
- **프롬프트에 개인정보가 없다** — 학년·단원명·문제 본문·난이도뿐. R4 준수
- **`ProblemResponse` 에 `answer`/`explanation` 이 없다** — 조회 4종 모두 정답 미포함
- **`getAuthorities()` 가 `ROLE_` 접두사를 그대로 둔다** — Phase 4 에서 `hasRole("ADMIN")` 이 바로 맞는다. 자주 틀리는 자리
- **Lombok 사용이 정확하다** — `@Builder.Default` 가 필요한 필드 5개에 **빠짐없이** 있다.
  이 영역에서 헛지적할 자리를 못 찾았다
- **FastAPI 가 async 를 옳게 쓴다** — `verify_token` 은 동기 `def`(스레드풀), LLM 은 `ainvoke`,
  Redis 는 `redis.asyncio`. `async def` 안에서 블로킹하는 자리가 **없다**

---

## §5. 이번에 안 본 것

| 안 본 것 | 왜 · 무엇이 미확정인가 |
| --- | --- |
| **빌드·실행·테스트** | JDK 없음. 🔴 **⚠️추정 9건은 실물 확인 전까지 확정이 아니다** |
| **실제 DB · 실제 자료** | 🔴 **다음에 확인할 첫 번째 항목**: `SELECT COUNT(*) FROM PROBLEMS WHERE ANSWER LIKE '%\%'` — 🔴-1 이 실제 자료에서 몇 건에 영향을 주는지 모른다. 0건이면 영향 없고, 많으면 **채점이 이미 틀리고 있다** |
| **Gemini 실호출** | 규율대로 부르지 않았다. 🟠-14 가 추정인 이유 |
| **프론트엔드** | 레포에 없다 |
| **`docs/` 문서 자체** | 코드 대조용으로만 읽었다 |
| **AI Hub 원자료** | `research/aihub-json-structure.md` 를 안 읽었다 — 🟠-11·🟠-12 는 **실제 JSON 에 그 필드가 빠진 건이 있는지**에 달렸다 |
| **커밋 이력·브랜치 비교** | 작업 트리 현재 상태만 |
| **성능·부하** | 🟠-16·🟡-24·🟡-28 전부 읽어서 안 것이고 재 본 것이 아니다 |

---

## §6. 판정

**REQUEST CHANGES** — 🔴 5건이 전부 **제품의 핵심 고리 또는 인증 경계**에 있다.

리뷰어의 우선순위 의견:
> `TODOS.md` 1절·2절이 「지금 할 일」로 잡혀 있는데, 🔴-3(반복 제출)과 🔴-4(챗봇 신원 미전달)는
> **그 목록을 다 끝내도 남는다.** 특히 🔴-4 는 **적혀 있는 수정 방법대로 고치면 「고쳐졌다」고
> 보이면서 실제로는 안 고쳐지는** 형태라, 착수 전에 한 번 짚어 둘 만하다.

---

## 결정 기록

> 🔴 **아래 「결정」 칸은 2026-08-27 리뷰 당일 상태다.** 처리 결과는 이 절 아래에 이어 붙인다.
> 위 지적 본문은 그날의 기록이므로 고치지 않는다.

| # | 지적 | 심각도 | 근거 | 결정 |
| --- | --- | --- | --- | --- |
| 1 | 정규화가 다른 답을 같게 만든다 | 🔴 | 확인함 | 🔴 그대로 — **실자료 영향 범위 확인이 먼저** |
| 2 | 리프레시 토큰이 액세스로 통한다 | 🔴 | 코드 대조 | 🔴 그대로 |
| 3 | 반복 제출로 정답률 조작 | 🔴 | 코드 대조 | 🔴 그대로 |
| 4 | 챗봇에 학생 신원 미전달 | 🔴 | 코드 대조 | 🔴 그대로 — **TODOS 2절 서술 정정 필요** |
| 5 | 무인증 Redis 에 토큰·블랙리스트 | 🔴 | 코드 대조 | 🔴 그대로 |
| 6~23 | §2 중대 18건 | 🟠 | 표 참고 | 🔴 전부 그대로 |
| 24~40 | §3 경미 17건 | 🟡 | 표 참고 | 🔴 전부 그대로 |

**기지로 내린 것** — 리뷰어가 문서를 읽고 스스로 8건을 「새 지적」에서 내렸다.
자가채점 · snake_case · `supabase` 미사용 · `ddl-auto` · `isCorrect` 키 이름 ·
`RestTemplate` 타임아웃 · 트랜잭션 안 HTTP · 유저 이중 조회(일부).

**남은 위험** — §5. 특히 🔴 **⚠️추정 9건과 실자료 미확인.**

---

# 결정 기록 — 2026-08-27 (프론트엔드 확인 후)

> 🔴 **위 지적 본문은 그날의 기록이라 고치지 않았다.** 확정 결과는 여기를 본다.

## 검증한 것 — 전부 재현·확인됨

| 지적 | 어떻게 확인했나 | 결과 |
| --- | --- | --- |
| 🔴-1 정규화 충돌 | 같은 정규식을 **독립적으로 다시 실행** | ✅ 재현. `√2`=`2`, `2√2`=`√2/2`=`22`, `$\pi$`=`$`=빈 문자열 |
| 🔴-2 토큰 종류 없음 | `JwtTokenProvider.java:25~41` 확인 | ✅ subject·서명·클레임 동일, 만료만 다름 |
| 🔴-4 챗봇 신원 | `AiChatController` 에 `@AuthenticationPrincipal` grep | ✅ 0건 |
| 🟠-6 `UserStatus` 미사용 | `getStatus()` grep | ✅ 0건 (생성 시 `ACTIVE` 2곳뿐) |
| 🟠-21 `prod` 분기 도달 불가 | `ls src/main/resources/` | ✅ `application-prod.yml` 없음 |
| 🟠-19 스크립트 재실행 거짓 실패 | `normalize_answers.py:60~101` 확인 | ✅ `"1"` 은 원문자도 `(1)` 도 없어 `failed` 로 분류 |
| 🟡-25 `@Cacheable` 0개 | grep | ✅ 0건 |
| 🟡-30 `LearningContent` 없음 | grep | ✅ 파일 없음 |

## 🔴-1 — 범위를 좁힌다 (지적은 유효)

`$\frac{3}{4}$` → `3/4` 는 **정상 동작한다.** 이 함수가 전부 틀린 것이 아니라,
**모르는 명령어를 만났을 때만** 무너진다. 대응도 그 범위로 좁힌다 —
아는 표기는 그대로 두고, 모르는 명령어는 지우지 말고 「비교 불가」를 반환한다.

📌 **실자료에 LaTeX 정답이 있다는 실물 근거를 찾았다.** 프론트에
`MathText.tsx` 의 `dedupeUnicode` 가 있다 — DB 에 `\times ×`, `\alpha α` 처럼
**LaTeX 와 유니코드가 둘 다** 들어 있는 경우를 12종 우회하고 있다.
🔴 이 지적이 「0건일 수도 있다」가 아니라 **거의 확실히 실재한다**는 뜻이다.

## 🔴-3 — 유효. 다만 지금은 API 로만 가능하다

프론트에 **맞춤 문제·랜덤 문제 화면이 없어서**(아래) 반복 제출을 하는 UI 경로가 없다.
API 로는 그대로 가능하므로 지적은 유효하고, 🎯 **화면을 만들기 전에 닫는 것이 맞다.**

## 🔴-4 — 유효. `TODOS.md` 2절을 정정했다

이 리뷰의 가장 값이 큰 발견이다. 적혀 있던 수정 방법을 그대로 따르면
`ailang:chat:service@internal:{session_id}` 가 되어 **안 고쳐진다.**
`TODOS.md` 2절에 정정 사유와 올바른 순서(①Spring→②요청 바디→③FastAPI 키)를 적었다.

## 프론트 확인으로 새로 안 것

🔴 **백엔드 엔드포인트 다섯 개가 아무 화면에서도 안 불린다**:
`/adaptive` · `/random` · `/random-by-grade` · `/{id}/concept` · `/ai-generated`

이 리뷰가 「제품의 핵심 고리」라 부른 것이 **아직 화면에 존재하지 않는다.**
학생이 문제를 푸는 유일한 경로는 진단 테스트 20문제다.
그런데 진단도 `submitAnswer` 를 부르므로 **통계는 이미 갱신되고 있다** —
20문제가 여러 챕터에 흩어져 `totalCount < 3` 에 머물면서 정답률 분모만 오염된다.

## 이 리뷰가 놓친 것 (내가 추가로 찾음)

🔴 **`DataLoader` 가드가 `SOURCE_TYPE` 을 안 가린다.** 가드는
`problemRepository.count() > 0`(`DataLoader.java:67`)인데 `getAiProblem` 이 **같은 표에**
AI 문제를 저장한다(`ProblemServiceImpl.java:233`). 적재 경로가 틀려 0건인 상태에서
AI 문제가 **한 건만** 생기면 다음 부팅부터 **기출 적재를 영원히 건너뛴다.**
→ `TODOS.md` 4절에 추가.

## ⚠️ 확인했지만 지적하지 않기로 한 것

`ProblemResponse.of` 의 `problem.getChapter().getId()` 가 N+1 인지 의심해 확인했는데,
**필드 접근 매핑이라 Hibernate 프록시가 초기화될지 갈린다.** 단정할 수 없어 지적하지
않는다. 실측 항목으로만 남긴다.

## 나머지 — 🔴 그대로

🔴-5 · 🟠-6 ~ 🟠-23 · 🟡-24 ~ 🟡-40 은 전부 유효하며 `TODOS.md` 에 반영했거나
반영 대기다. **⚠️추정 9건은 여전히 실물 확인 전이다.**
