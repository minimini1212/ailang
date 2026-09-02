# API 계약 — 엔드포인트가 무엇을 받고 무엇을 주는가

> **최종 갱신: 2026-08-27** — **코드에서 읽어 확인한 목록**이다. 기획 문서가 아니다.
> 엔드포인트·요청 필드·응답 필드·상태코드를 바꾸면 **같은 작업 안에서** 이 문서를 갱신한다.
>
> ⚠️ 옛 `PROJECT_PLANNER.md` 는 토큰 재발급을 `/api/auth/refresh` 로 적어 두었다.
> 실제 경로는 **`/api/auth/reissue`** 다. 그리고 진단 테스트·정답 공개·AI 모의문제·
> 자가채점은 **아예 적혀 있지 않았다.** 그래서 이 문서가 따로 있다.

## §0. 이 문서를 처음 보는 사람에게

| 말 | 뜻 |
| --- | --- |
| **공통 응답 봉투** | 모든 Spring 응답이 `code`·`message`·`data` 세 칸으로 감싸인다. 실제 내용은 `data` 안에 있다. |
| **HttpOnly 쿠키** | 자바스크립트가 읽을 수 없는 쿠키. 토큰을 여기 담아 탈취를 어렵게 한다. |
| **기출 / AI 생성** | 사람이 낸 문제 / Gemini 가 만든 문제. 어휘 설명은 [`DATA_CONTRACT.md`](DATA_CONTRACT.md) §0. |
| **맞춤 문제(adaptive)** | 그 학생의 그 챕터 정답률로 정해진 난이도의 문제. |
| **진단 테스트(assessment)** | 처음 온 학생의 수준을 보려고 한 번에 주는 20문제. |

## §1. 공통

- **Spring Boot**: `http://localhost:8080` — 브라우저가 부른다.
- **FastAPI**: `http://localhost:8001` — 🔴 **원래 Spring 만 부르는 서버다.**
  그런데 compose 가 호스트에 포트를 열어 두어 브라우저에서도 닿는다
  ([`../TODOS.md`](../TODOS.md) 2절).

**인증**: `accessToken` **HttpOnly 쿠키**. 🎯 `Authorization` 헤더가 아니다 —
`JwtAuthenticationFilter` 는 쿠키에서만 토큰을 꺼낸다. FastAPI 쪽은 반대로 **헤더**를 쓴다.

⚠️ **쿠키 이름은 카멜케이스다** (`accessToken` · `refreshToken`, `CookieUtil` 의 상수).
옛 `PROJECT_PLANNER.md` 는 `access_token` · `refresh_token` 으로 적어 두었는데 **틀린 값**이다.

**응답 봉투** (Spring 전용. FastAPI 는 봉투 없이 바로 준다)

```json
{ "code": 200, "message": "요청이 성공적으로 처리되었습니다.", "data": { } }
```

에러도 같은 모양이고 `data` 는 `null` 이다. 메시지 원문은 `ErrorCode` enum 에 있다.

⚠️ **`code` 는 HTTP 상태코드와 같은 값이다.** 별도 에러 코드 체계가 아니다.
그래서 「같은 400 안에서 무엇이 잘못됐나」는 메시지 문자열로만 구분된다.

---

## §2. 인증 `/api/auth` — 🔓 비인증 허용

| 메서드 | 경로 | 요청 | 응답 `data` |
| --- | --- | --- | --- |
| POST | `/email/send` | `{email}` | `null` |
| POST | `/email/verify` | `{email, code}` | `null` |
| POST | `/signup` | `{email, password, nickname, grade}` | `null` |
| POST | `/login` | `{email, password}` | `null` + 쿠키 2개 |
| POST | **`/reissue`** | 없음 (refresh 쿠키) | `null` + 쿠키 2개 |
| POST | `/logout` | 없음 (access 쿠키) | `null` |

`grade` 허용값: `ELEM_3` `ELEM_4` `ELEM_5` `ELEM_6` `MIDDLE_1` `MIDDLE_2` `MIDDLE_3` `HIGH_1`

**로그인·재발급이 심는 쿠키**: `accessToken`(1시간), `refreshToken`(7일). 둘 다 HttpOnly.

| 프로필 | SameSite | Secure |
| --- | --- | --- |
| `local` (기본) | `Lax` | 아니오 |
| `prod` | `None` | 예 |

⚠️ **`Lax` 는 크로스 사이트 POST 에 쿠키를 안 보낸다.** 로컬 CSRF 방어로 일부러 넣은
것이므로(`CookieUtil` 주석), 프론트를 다른 출처에서 띄우면 「로그인은 됐는데 API 가 401」이
된다. 그때 이 값을 푸는 대신 출처를 맞추는 쪽이 맞다.

**로그아웃**: access 토큰을 Redis 블랙리스트(`blacklist:access:`)에 **남은 만료시간만큼**
넣고 쿠키를 지운다. refresh 는 `refresh:token:{email}` 로 보관된다.
🔴 이 블랙리스트를 **FastAPI 는 보지 않는다.**

| 상태 | 메시지 |
| --- | --- |
| 400 | 이메일 인증이 필요합니다. / 인증 코드가 만료되었습니다. / 인증 코드가 일치하지 않습니다. / 존재하지 않는 회원입니다. / 비밀번호가 틀렸습니다. |
| 409 | 이미 가입된 이메일입니다. |
| 401 | 리프레시 토큰이 만료되었습니다. |
| 404 | 리프레시 토큰을 찾을 수 없습니다. |

### 구글 로그인

`GET /oauth2/authorization/google` → 구글 → 성공 시 `http://localhost:5173/oauth2/callback`
로 리다이렉트(쿠키 발급), 실패 시 `?error={메시지}`.

🔴 **구글 가입은 학년을 받지 않는다.** `USERS.GRADE` 가 null 인 유저가 여기서 생기고,
학년을 쓰는 엔드포인트 다섯 개가 그 유저에게 500 을 낸다 (§4·§5 의 ⚠️ 표시).

---

## §3. 유저 `/api/users` — 🔒 인증 필요

| 메서드 | 경로 | 응답 `data` |
| --- | --- | --- |
| GET | `/me` | `{id, email, nickname, grade, profileImageUrl, provider, role, assessmentCompleted}` |
| PATCH | `/me/assessment` | `null` — 진단 테스트 완료로 표시 |

⚠️ `PATCH /me/assessment` 는 **진단 테스트를 실제로 풀었는지 확인하지 않는다.** 부르면 켜진다.

---

## §4. 챕터 `/api/chapters`

| 메서드 | 경로 | 인증 | 설명 |
| --- | --- | --- | --- |
| GET | `/api/chapters` | 🔒 필요 | 내 학년 챕터 + 내 통계 |
| GET | `/api/chapters?grade=MIDDLE_3` | 🔓 불필요 | 그 학년 챕터 (통계 없음) |

**하나의 경로가 파라미터 유무로 두 가지 일을 한다.** `SecurityConfig` 는 `GET /api/chapters`
전체를 `permitAll` 로 열어 두었다.

🔴 **그래서 비로그인 상태로 `grade` 없이 부르면 500 이 난다** — 인증이 없어
`userDetails` 가 null 인데 바로 `.getEmail()` 을 부른다.
⚠️ **`grade` 에 없는 값을 넣어도 500 이다** — `Grade.valueOf` 가 던지는
`IllegalArgumentException` 을 받는 핸들러가 없다.

**응답 `data`** (배열)

```json
[{
  "id": 1, "title": "분수", "description": null, "orderNum": 1, "grade": "ELEM_3",
  "myStats": { "correctCount": 8, "totalCount": 10, "currentDifficulty": "HIGH", "correctRate": 80.0 }
}]
```

- `description` 은 적재된 챕터면 항상 `null` 이다 ([`DATA_CONTRACT.md`](DATA_CONTRACT.md) §2).
- `grade` 파라미터로 부르면 `myStats` 는 `null`.
- 한 번도 안 푼 챕터의 `currentDifficulty` 는 `MEDIUM`, `correctRate` 는 `0.0`.
  ⚠️ **이 `0.0` 은 「0% 맞혔다」가 아니라 「아직 안 풀었다」다.** 화면에서 구분해 보여줘야 한다.

---

## §5. 문제 `/api/problems` — 🔒 전부 인증 필요

| 메서드 | 경로 | 무엇 |
| --- | --- | --- |
| GET | `/adaptive?chapterId=` | 내 난이도에 맞는 **기출** 1개 |
| GET | `/random?chapterId=` | 그 챕터 **기출** 랜덤 1개 |
| GET | `/random-by-grade` | ⚠️ 내 학년 **기출** 랜덤 1개 |
| GET | `/assessment` | ⚠️ 진단용 20문제 (하 7 · 중 7 · 상 6, 섞어서) |
| GET | `/ai-generated?chapterId=` | 🆕 Gemini 가 **지금 만든** 문제 1개 (DB 에 저장됨) |
| POST | `/{problemId}/submit` | 답안 제출 |
| GET | `/{problemId}/answer` | 🔴 정답 공개 |
| GET | `/{problemId}/concept` | ⚠️ 개념 설명 (Gemini) |

⚠️ = 학년을 쓰므로 **구글 가입 유저(학년 null)에게 500**.

### 문제 응답 (조회 4종 공통)

```json
{
  "id": 42, "chapterId": 1,
  "difficulty": "MEDIUM", "problemType": "MULTIPLE_CHOICE", "sourceType": "REAL",
  "question": "3/4 + 1/4 = ?",
  "options": null,
  "myStats": { "currentDifficulty": "MEDIUM", "correctRate": 60.0, "totalCount": 10 }
}
```

- 🔴 **`answer` 와 `explanation` 은 없다.** 여기 추가하지 말 것.
- ⚠️ **`options` 는 기출문제면 항상 `null`이다** — 객관식이어도 그렇다. 보기가 `question`
  본문 안에 텍스트로 들어 있다. AI 생성 문제만 JSON 배열 문자열을 갖는다.
- 🎯 **`sourceType` 을 화면에 반영할 것.** `AI` 는 정답이 검증되지 않은 문제다.
- `/assessment` 는 이 객체의 **배열**이고, `myStats` 는 전부 기본값(`MEDIUM`/`0.0`/`0`)이다.

**404**: 「해당 조건에 맞는 문제가 없습니다.」 — 그 챕터·난이도에 기출이 없을 때.
⚠️ `/assessment` 는 문제가 부족해도 **404 가 아니라 20개보다 적은 배열**을 준다. 0개일 수도 있다.

### POST `/{problemId}/submit`

```json
{ "chapterId": 1, "userAnswer": "1", "selfJudge": true }
```

| 필드 | 필수 | 설명 |
| --- | --- | --- |
| `chapterId` | ✓ | 🔴 **문제가 이 챕터에 속해야 한다.** 아니면 400 |
| `userAnswer` | ✓ | 객관식은 보기 번호(`"1"`~`"4"`), 단답형은 답 문자열 |
| `selfJudge` | 단답형은 **필수** | 🔴 빠지면 **400**. 예전에는 조용히 «오답» 으로 기록됐다 |

**채점**
- 객관식 → 서버가 `AnswerNormalizer` 로 표기를 맞춘 뒤 비교한다.
  원문자(`①`) · 달러(`$2$`) · 공백 · `\frac` · `\times` 는 흡수한다.
  🔴 **모르는 표기는 지우지 않는다** — 지우면 `$\sqrt{2}$` 와 `2` 가 같아진다.
  정규화 결과가 비면(`"$"` 등) 비교하지 않고 오답으로 둔다.
- 단답형 → 학생 자가 채점(`selfJudge`). ⚠️ 값이 없으면 거부한다.

**⚠️ 통계에는 「문제당 첫 제출」만 반영된다.** 같은 문제를 다시 내면 이력에는 남지만
정답률은 안 움직인다 — 제출 응답이 정답을 알려주므로, 안 막으면 반복 제출로
정답률을 원하는 값으로 만들 수 있다.

| 상태 | 언제 |
| --- | --- |
| 400 | 문제와 챕터가 맞지 않음 · 단답형인데 `selfJudge` 없음 |
| 404 | 문제·챕터가 없음 |

규율: [`rules/grading-and-difficulty.md`](rules/grading-and-difficulty.md)

**응답 `data`**

```json
{ "correct": true, "correctAnswer": "2",
  "explanation": "…", "updatedDifficulty": "HIGH", "updatedCorrectRate": 80.0 }
```

🔴 **정답 여부의 키 이름은 `isCorrect` 가 아니라 `correct` 다. 바꾸지 말 것.**

자바 필드는 `boolean isCorrect`(`SubmitAnswerResponse.java:18`)인데, Lombok 이 만드는 읽기
메서드가 `isCorrect()` 이고, Jackson 은 `is` 로 시작하는 boolean 읽기 메서드에서 **`is` 를
떼는** JavaBeans 규약을 따른다. 그래서 나가는 키는 `correct` 다.

🎯 **프론트가 이미 `correct` 로 받고 있다** — 근거까지 주석으로 적혀 있다
(`src/types/api.types.ts:42`, `Assessment.tsx:106`). **지금 정상 동작한다.**
⚠️ 자바 필드명을 `correct` 로 바꾸거나 `@JsonProperty("isCorrect")` 를 붙이면
**멀쩡한 화면이 깨진다.** 바꾸려면 프론트와 동시에 바꿔야 한다.

> 🔄 **2026-08-27 정정.** 이 문서가 원래 `isCorrect` 로 적고 있었다. 옛
> `PROJECT_PLANNER.md` 의 값을 확인 없이 옮긴 것이다. 프론트 코드를 보고 정정했다.
> 📌 다른 boolean 필드는 영향이 없다 — `UserInfoResponse.assessmentCompleted` 는
> 필드명이 `is` 로 시작하지 않아 이름이 그대로 나간다.

### GET `/{problemId}/answer` — 단답형 전용

```json
{ "answer": "2", "explanation": "…" }
```

단답형 자가채점 흐름(**정답 공개 → 학생이 판단 → 제출**)을 위한 창구다.

🔴 **객관식이면 400** 「이 문제는 정답을 미리 볼 수 없습니다」.
객관식은 서버가 채점하므로 정답을 미리 줄 이유가 없다.
예전에는 유형을 안 봐서 객관식 정답도 제출 전에 나갔다.

⚠️ 「이미 제출했는지」는 **확인하지 않는다.** 자가채점은 공개가 제출보다 먼저이므로,
그 검사를 넣으면 흐름이 통째로 막힌다.

### GET `/{problemId}/concept`

Spring → FastAPI `/ai/concept` → Gemini. 응답 `{ "concept": "…" }`.
설명 안의 수식은 `$...$` / `$$...$$` (LaTeX) 로 온다 — 화면에 수식 렌더러가 필요하다.

⚠️ Gemini 할당량이 떨어지면 FastAPI 는 429 를 주는데 **학생은 500 을 본다** (§7).

---

## §6. 챗봇 `/api/ai` — 🔒 인증 필요

`POST /api/ai/chat` → `{ "question": "...", "sessionId": "..." }`
→ `{ "answer": "...", "sessionId": "..." }`

🔴 **`sessionId` 는 클라이언트가 정하는 값이고, 서버는 그걸 그대로 대화 저장 키로 쓴다.**
로그인한 아무나 남의 `sessionId` 를 넣으면 그 사람의 이전 대화가 답변 맥락에 실린다.
대화는 Redis 에 1시간 보관된다.

⚠️ 이 엔드포인트는 **RAG 를 쓰지 않는다.** 이름이 `RagService` 지만 검색은 TODO 한 줄이고,
지금은 대화 이력만 얹어 Gemini 에 그대로 넘긴다.

---

## §7. FastAPI `http://localhost:8001` — Spring 내부 호출용

**인증**: `Authorization: Bearer {JWT}` **헤더**. Spring 이 `service@internal` 을 subject 로
직접 만들어 붙인다. 봉투 없이 바로 JSON 을 준다.

| 메서드 | 경로 | 요청 | 응답 |
| --- | --- | --- | --- |
| POST | `/ai/concept` | `{question, grade, chapter_title}` | `{concept}` |
| POST | `/ai/problem` | `{chapter_title, difficulty, grade}` | `{question, problem_type, options, answer, explanation}` |
| POST | `/ai/chat` | `{question, session_id}` | `{answer, session_id}` |
| GET | `/health` | — | `{status: "ok"}` |

🎯 **필드 이름이 snake_case 다.** Spring 쪽 DTO(`AiServerClient` 내부 클래스)도 그래서
`chapter_title` 같은 자바답지 않은 이름을 쓴다 — 의도된 것이니 「정리」하지 말 것.

**상태코드**

| FastAPI 가 내리는 것 | 언제 | Spring 을 거치면 |
| --- | --- | --- |
| **429** | 모델 요청 한도 초과 | **429** 「지금은 이용이 몰리고 있어요」 |
| **502** | 응답이 JSON 이 아님·형식 위반·입력이 한도 초과 | **502** 「AI 응답을 처리하지 못했습니다」 |
| **503** | 토큰·권한 문제, 서버 무응답, 서비스 종료(410) | **503** 「일시적으로 이용할 수 없습니다」 |
| 401 | 토큰 없음·서명 불일치·만료 | 503 (학생이 손쓸 수 없는 일이라 묶는다) |

> 🔄 **2026-09-01 정정.** 예전에는 위 네 가지가 **전부 500 「서버 내부 에러」** 하나로
> 보였다. `RestTemplate` 이 던진 예외가 `GlobalExceptionHandler` 의 `RuntimeException`
> 분기까지 흘러갔기 때문이다. 이제 `AiServerClient` 가 상태코드를 보존한다.
>
> ⚠️ **타임아웃**: 연결 5초 · 읽기 60초 (`ai.server.connect-timeout-ms` /
> `read-timeout-ms`). 읽기가 긴 이유는 실측 응답이 **13~19초**이기 때문이다 —
> PRD §4 의 「≤ 5초」를 이미 넘고 있다. 짧게 잡으면 정상 응답까지 끊긴다.
>
> 규율: [`rules/ai-call-policy.md`](rules/ai-call-policy.md)

---

## §8. 아직 없는 것

| | 상태 |
| --- | --- |
| 관리자 API (`/api/admin/**`) | ⬜ 컨트롤러가 없다. `SecurityConfig` 에 **역할 기반 규칙도 하나도 없다** |
| RAG 업로드 API | ⬜ Phase 3 |
| API 문서 자동화 (Swagger/springdoc) | ⬜ 미도입 — 그래서 이 문서가 손으로 관리된다 |

⚠️ **손으로 관리되는 문서는 낡는다.** 엔드포인트를 추가하면 이 파일도 같이 고친다.
springdoc 도입은 [`../TODOS.md`](../TODOS.md) 에 올릴 것.
