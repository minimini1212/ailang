# 데이터 계약 — 표·컬럼·어휘·환경변수의 진실

> **최종 갱신: 2026-08-27** — 코드에서 확인한 상태다.
> 스키마·필드·환경변수를 바꾸면 **같은 작업 안에서** 이 문서와 [`../.env.example`](../.env.example)
> 을 갱신한다. 두 곳이 갈리면 다음 사람은 어느 쪽이 맞는지 알 수 없다.

## §0. 이 문서를 처음 보는 사람에게

| 말 | 뜻 |
| --- | --- |
| **데이터 계약** | 「어떤 자료를 어떤 이름으로 어디에 넣는가」를 못 박아 둔 약속. 코드를 쓰기 전에 여기부터 읽는다. |
| **엔티티 / 표** | 자바 클래스 하나가 Oracle 표 하나에 대응한다. `Problem` 클래스 ↔ `PROBLEMS` 표. |
| **enum(어휘)** | 정해진 값만 쓸 수 있는 목록. 「난이도는 하·중·상 셋뿐」 같은 것. 🔴 문자열로 손으로 적지 않는다. |
| **기출 / AI 생성** | 사람이 낸 문제(정답 검증됨) / Gemini 가 만든 문제(검증 안 됨). 같은 표에 있고 `SOURCE_TYPE` 으로 갈린다. |
| **정답률** | 그 학생이 그 챕터에서 맞힌 수 ÷ 푼 수. 다음 문제의 난이도를 정하는 값이다. |
| **시퀀스(SEQ)** | Oracle 이 기본키 번호를 발급하는 장치. 표마다 하나씩 있다. |

---

## §1. 개념 분리 — 🔴 이것만은 섞지 않는다

| 개념 | 설명 | 🔴 주의 |
| --- | --- | --- |
| **Chapter (챕터)** | 학년별 단원. 「초3 - 분수」 | 학년을 자기가 들고 있다. 문제는 학년을 직접 안 갖는다 |
| **Problem (문제)** | 한 문제. 본문·정답·해설·난이도·유형·출처 | 🔴 **기출과 AI 생성이 같은 표에 있다.** `SOURCE_TYPE` 없이 조회하면 섞인다 |
| **History (풀이 이력)** | 학생이 문제를 푼 **한 건**. 되돌아볼 수 있는 사실 | 지워지지 않는다. 통계는 여기서 다시 계산할 수 있어야 한다 |
| **Stats (챕터 통계)** | 학생 × 챕터 하나당 한 줄. 누적 정답 수·푼 수·현재 난이도 | 🔴 History 의 **요약**이다. 둘이 어긋나면 History 가 맞다 |
| **User (유저)** | 학생. 학년을 가진다 | ⚠️ `grade` 는 **null 일 수 있다** (구글 가입은 학년을 안 받는다) |

### 왜 History 와 Stats 를 나누나

Stats 만 있으면 「이 학생이 3번 문제를 언제 어떻게 틀렸나」를 영영 답할 수 없다.
History 만 있으면 문제를 낼 때마다 이력 전체를 세어야 한다. **둘 다 필요하다.**

🔴 **Stats 는 언제든 History 로부터 다시 만들 수 있어야 한다.** 지금은 아니다 —
`recordAnswer` 가 누적값만 갱신하고, 난이도는 그 시점의 값에서 한 칸씩 움직이므로
**같은 이력을 다시 재생해도 같은 난이도가 나오지 않는다.** 이 성질이 필요해지면
(예: 잘못 채점된 건을 정정할 때) 재계산 경로를 먼저 만들어야 한다.

### 왜 기출과 AI 생성을 나누나

AI 가 만든 문제는 **정답이 검증되지 않았다.** 그런데 학생의 정답률은 그 정답으로 계산되고,
정답률이 다음 문제의 난이도를 정한다. 섞이면 검증 안 된 정답이 학습 경로를 정하게 된다.
자세한 규율: [`rules/grading-and-difficulty.md`](rules/grading-and-difficulty.md)

---

## §2. 표 — 확정

모든 표는 `BaseTimeEntity` 를 상속해 **`CREATED_AT` · `UPDATED_AT`** 을 자동으로 가진다
(`@EnableJpaAuditing`, `AppConfig`). 기본키는 전부 Oracle 시퀀스, `allocationSize = 50`.

⚠️ **`allocationSize = 50` 의 뜻**: 번호를 50개씩 미리 받아 쓴다. 애플리케이션을 재시작하면
안 쓴 번호는 버려지므로 **id 에 구멍이 생긴다. 정상이다** — id 를 「몇 건인가」로 읽지 말 것.

### USERS

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `ID` | NUMBER (PK) | `USERS_SEQ` |
| `EMAIL` | VARCHAR2(100) NOT NULL **UNIQUE** | 로그인 식별자. 🔴 프롬프트·로그에 넣지 않는다 |
| `PASSWORD` | VARCHAR2(200) | BCrypt. 구글 가입이면 null |
| `NICKNAME` | VARCHAR2(50) NOT NULL | |
| `PROVIDER` | VARCHAR2 NOT NULL | `LOCAL` / `GOOGLE` |
| `PROVIDER_ID` | VARCHAR2(100) | 구글 sub |
| `STATUS` | VARCHAR2 NOT NULL | `ACTIVE` / `SUSPENDED` |
| `ROLE` | VARCHAR2 NOT NULL | `ROLE_USER` / `ROLE_ADMIN` |
| `PROFILE_IMAGE_URL` | VARCHAR2(500) | |
| `GRADE` | VARCHAR2(20) | ⚠️ **nullable** — `ELEM_3`~`HIGH_1` |
| `ASSESSMENT_COMPLETED` | NUMBER(1) NOT NULL DEFAULT 0 | 진단 테스트를 마쳤나 |

🔴 **`GRADE` 가 null 인 유저가 실제로 생긴다.** 회원가입 폼은 학년을 필수로 받지만 구글
OAuth2 경로(`CustomOAuth2UserService`)는 받지 않는다. `user.getGrade().name()` 을 부르는
자리가 셋 있고 전부 NPE 로 500 이 된다 ([`../TODOS.md`](../TODOS.md) 2절).

### CHAPTERS

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `ID` | NUMBER (PK) | `CHAPTERS_SEQ` |
| `GRADE` | VARCHAR2(20) NOT NULL | 🎯 **학년은 챕터가 들고 있다** |
| `TITLE` | VARCHAR2(100) NOT NULL | 「분수」, 「방정식과 부등식」 |
| `DESCRIPTION` | VARCHAR2(500) | ⚠️ `DataLoader` 는 이 값을 안 채운다 (항상 null) |
| `ORDER_NUM` | NUMBER NOT NULL | 같은 학년 안에서의 표시 순서 |

⚠️ **`(GRADE, TITLE)` 에 유니크 제약이 없다.** `DataLoader` 는 캐시 + `findByGradeAndTitle`
로 중복을 피하지만, 그건 **한 프로세스 안에서만** 통하는 보호다.

### PROBLEMS

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `ID` | NUMBER (PK) | `PROBLEMS_SEQ` |
| `CHAPTER_ID` | NUMBER NOT NULL (FK) | 🎯 문제는 학년을 직접 모른다 — 챕터를 타고 간다 |
| `DIFFICULTY` | VARCHAR2(10) NOT NULL | `LOW` / `MEDIUM` / `HIGH` |
| `PROBLEM_TYPE` | VARCHAR2(20) NOT NULL | `MULTIPLE_CHOICE` / `SHORT_ANSWER` |
| `QUESTION` | CLOB NOT NULL | 문제 본문 |
| `OPTIONS` | VARCHAR2(**1000**) | 객관식 보기 JSON 배열. 단답형이면 null |
| `ANSWER` | VARCHAR2(200) NOT NULL | 🔴 절대 제출 전에 응답에 실리지 않는다 |
| `EXPLANATION` | CLOB NOT NULL | 해설 |
| `SOURCE_TYPE` | VARCHAR2(10) NOT NULL DEFAULT `REAL` | 🔴 `REAL`(기출) / `AI`(생성) |

🔴 **`OPTIONS` 의 1000자는 AI 생성 문제에서 실제로 넘칠 수 있다.** LaTeX 가 섞인 보기 4개는
`["$\\frac{3}{4}$", ...]` 처럼 이스케이프까지 붙어 길어진다. 넘치면 `DataException` → 500.
저장 전에 길이를 검사한다 ([`rules/ai-call-policy.md`](rules/ai-call-policy.md)).

⚠️ **`DataLoader` 로 들어온 기출문제는 `OPTIONS` 가 항상 null 이다.** 보기가 `QUESTION`
본문 안에 텍스트로 들어 있기 때문이다 — 객관식이어도 그렇다. 화면에서 보기를 따로 그리려면
이 사실을 먼저 알아야 한다 ([`research/aihub-json-structure.md`](research/aihub-json-structure.md)).

### USER_PROBLEM_HISTORY

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `ID` | NUMBER (PK) | `USER_PROBLEM_HISTORY_SEQ` |
| `USER_ID` | NUMBER NOT NULL (FK) | |
| `PROBLEM_ID` | NUMBER NOT NULL (FK) | |
| `USER_ANSWER` | VARCHAR2(200) NOT NULL | 학생이 낸 답 **원문 그대로** |
| `IS_CORRECT` | NUMBER(1) NOT NULL | 🔴 지금 이 값의 출처가 문제다 — §3 |
| `CREATED_AT` | TIMESTAMP | = 푼 시각 |

인덱스: `(USER_ID, CREATED_AT DESC)`

🎯 **`USER_ANSWER` 는 정규화하지 않은 원문을 넣는다.** 나중에 채점 규칙이 바뀌었을 때
「그때 학생이 정확히 뭐라고 썼나」를 되짚을 수 있어야 한다.

### USER_CHAPTER_STATS

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `ID` | NUMBER (PK) | `USER_CHAPTER_STATS_SEQ` |
| `USER_ID` | NUMBER NOT NULL (FK) | |
| `CHAPTER_ID` | NUMBER NOT NULL (FK) | |
| `CORRECT_COUNT` | NUMBER NOT NULL DEFAULT 0 | |
| `TOTAL_COUNT` | NUMBER NOT NULL DEFAULT 0 | |
| `CURRENT_DIFFICULTY` | VARCHAR2(10) NOT NULL DEFAULT `MEDIUM` | |

**UNIQUE `(USER_ID, CHAPTER_ID)`**

🔴 이 유니크 제약과 `submitAnswer` 의 getOrCreate 가 부딪힌다. 잠금 없는 read-then-write 라
같은 학생의 동시 제출에서 제약 위반이 난다.

### 관계

```
USERS 1 ─── N USER_PROBLEM_HISTORY N ─── 1 PROBLEMS
USERS 1 ─── N USER_CHAPTER_STATS   N ─── 1 CHAPTERS
CHAPTERS 1 ─── N PROBLEMS
```

---

## §3. 어휘 — 확정. 🔴 문자열로 손으로 적지 말 것

코드: `domain/problem/enums/`, `domain/user/enums/`

| enum | 값 | 어디에 |
| --- | --- | --- |
| `Grade` | `ELEM_3` `ELEM_4` `ELEM_5` `ELEM_6` `MIDDLE_1` `MIDDLE_2` `MIDDLE_3` `HIGH_1` | USERS, CHAPTERS |
| `Difficulty` | `LOW` `MEDIUM` `HIGH` | PROBLEMS, USER_CHAPTER_STATS |
| `ProblemType` | `MULTIPLE_CHOICE` `SHORT_ANSWER` | PROBLEMS |
| `SourceType` | `REAL` `AI` | PROBLEMS |
| `AuthProvider` | `LOCAL` `GOOGLE` | USERS |
| `UserRole` | `ROLE_USER` `ROLE_ADMIN` | USERS |
| `UserStatus` | `ACTIVE` `SUSPENDED` | USERS |

🔴 **지금 이 규율이 지켜지지 않는 자리가 있다.** `ProblemServiceImpl` 은 난이도를
`String` 으로 바꿔 들고 다니고 (`stats.getCurrentDifficulty().name()`), 통계가 없으면
`"MEDIUM"` 이라는 **문자열 리터럴**을 쓰고, 네이티브 쿼리에 `String` 으로 넘긴다.
`Difficulty.MEDIUM` 을 쓰면 오타가 컴파일에서 걸리지만 `"MEDUIM"` 은 조용히 0건을 준다.

⚠️ **FastAPI 쪽에도 같은 어휘가 복사돼 있다** — `concept_service.py`·`problem_service.py`
의 `GRADE_DISPLAY` 딕셔너리. 학년이 늘면 **세 곳**을 고쳐야 한다(자바 enum, 파이썬 두 곳).
`GRADE_DISPLAY.get(grade, grade)` 라 빠져도 예외가 안 나고 `"HIGH_2"` 가 그대로 프롬프트에
들어간다 — 조용히 이상한 설명이 나간다.

---

## §4. 「모른다」를 값으로 접지 않는다 — 확정

| 상황 | 지금 저장되는 값 | 🔴 저장돼야 할 것 |
| --- | --- | --- |
| 단답형인데 `selfJudge` 가 안 왔다 | **오답(`0`)** | 저장 안 함 — **400 으로 거부** |
| 그 챕터를 아직 3문제 미만 풀었다 | 난이도 유지 ✅ | (맞다) 「모른다」로 취급 중 |
| 학년을 아직 안 골랐다 (구글 가입) | — | 🔴 NPE 로 500. 「미입력」 상태가 필요하다 |
| AI 호출이 실패했다 | 500 (한 종류) | 한도초과 · 모델거부 · 형식오류 · 서버다운 구분 |
| 적재 때 건너뛴 문제 | `skipped` 카운터 하나 | 사유별 집계 (§5) |

🔴 **세 가지 상태가 필요한 자리에 boolean 을 쓰지 않는다.**
「맞음/틀림」에는 언제나 「아직 안 풀었음」이 따라온다.

---

## §5. 적재(DataLoader) 계약

원본 자료 구조: [`research/aihub-json-structure.md`](research/aihub-json-structure.md)

| 원본 | → | 우리 값 | 규칙 |
| --- | --- | --- | --- |
| `question_grade` | → | `Grade` | `E3`~`E6`→`ELEM_*`, `M1`~`M3`→`MIDDLE_*`, `H1`→`HIGH_1`. 모르면 건너뜀 |
| `question_topic_name` | → | `CHAPTERS.TITLE` | 없으면 챕터를 만든다 |
| `question_unit` | → | `CHAPTERS.ORDER_NUM` | ⚠️ `Integer.parseInt` — 숫자가 아니면 예외 |
| `question_step` | → | `Difficulty` | `기본`→LOW, `표준`→MEDIUM, `심화`→HIGH · 🔴 아래 |
| `question_difficulty` (1~5) | → | `Difficulty` | `question_step` 이 없을 때만. ≤2 LOW, ≤3 MEDIUM, 그 외 HIGH · 🔴 아래 |
| `question_type1` | → | `ProblemType` | `선택형`→MULTIPLE_CHOICE, 그 외 SHORT_ANSWER |
| `OCR_info[0].question_text` | → | `QUESTION` | 보기가 이 안에 들어 있다 |
| `answer_bbox` 중 `type=="answer"` | → | `ANSWER` | 첫 건만. 비면 건너뜀 |
| `answer_info[0].answer_text` | → | `EXPLANATION` | |
| (고정) | → | `SOURCE_TYPE` | 항상 `REAL` |

### 🔴 이 난이도 매핑은 실제 자료와 맞지 않는다 (2026-08-31 실측)

원본에 있는 `question_step` 값은 **`기본`(935) · `실생활응용`(217) 둘뿐**이다.
`표준`·`심화` 는 **한 건도 없다** — 위 표의 그 두 줄은 한 번도 쓰이지 않는다.
그리고 `question_difficulty` 의 최댓값이 **3** 이라 `> 3` 조건인 HIGH 도 안 나온다.

| | 하 | 중 | 상 |
| --- | --- | --- | --- |
| 지금 DB | 454 | 432 | 246 |
| 이 매핑이 만들 것 | **1,131** | **21** | **0** |

🔴 **즉 지금 DB 는 이 코드로 재현할 수 없다.** 비우면 되돌릴 수 없고, 재적재하면
난이도 축이 사실상 사라진다. 전말과 근거:
[`research/aihub-data-measurement-2026-08-31.md`](research/aihub-data-measurement-2026-08-31.md) §1

**멱등성**: 지금 유일한 보호는 `problemRepository.count() > 0` 이다.
🔴 **전부 아니면 전무다.** 절반만 들어간 상태에서 나머지를 채울 수 없고, 새 학년 자료를
추가할 수도 없다(이미 데이터가 있으니 통째로 건너뛴다). 진짜 키는 AI Hub 의 `id` 다 —
`PROBLEMS` 에 그 컬럼이 없어서 못 쓰고 있다 ([`../TODOS.md`](../TODOS.md) 참고).

**실패 분류**: 지금은 `skipped` 하나로 합쳐진다. 「답안 파일 없음」·「학년 코드 모름」·
「정답 추출 실패」·「파싱 예외」는 **다른 문제**이고 대응도 다르다.

---

## §5.5. Redis 에 무엇이 들어 있나 — 🔴 이게 없어서 위험이 안 보였다

> 🔄 **2026-08-27 신설.** 이 문서에 Oracle 표 4개만 있고 **Redis 키 목록이 없었다.**
> 그래서 「Redis 에 비밀번호를 안 건다」는 사실이 **얼마나 위험한지** 아무도 알 수 없었다.

| 키 | 값 | 무엇을 성립시키나 | 만드는 곳 |
| --- | --- | --- | --- |
| `refresh:token:{email}` | 리프레시 토큰 **원문** | 재발급. 🔴 **유출되면 그대로 로그인이다** | `AuthServiceImpl:127` · `OAuth2SuccessHandler:42` |
| `blacklist:access:{토큰}` | `"logout"` | **로그아웃의 유일한 근거.** 지우면 로그아웃이 되돌아간다 | `AuthServiceImpl:114` |
| `email:verify:code:{email}` | 6자리 숫자 (TTL 300초) | 이메일 인증 | `EmailVerificationServiceImpl:38` |
| `email:verified:{email}` | `"true"` (TTL 30분) | 🔴 **이걸 넣으면 이메일 인증 없이 가입된다** | `EmailVerificationServiceImpl:50` |
| `ailang:chat:{session_id}` | 대화 이력 리스트 (TTL 1시간) | 챗봇 맥락. ⚠️ **유저에 안 묶여 있다** | `rag_service.py:33` |

### 🔴 그래서 이 Redis 는 인증 없이 열려 있으면 안 된다

`docker-compose.yml` 이 `6380:6379` 로 **호스트 전체에** 바인딩하고 비밀번호가 없다.
닿을 수 있는 누구나 ① 전 학생의 리프레시 토큰을 읽고 ② 블랙리스트를 지워 로그아웃을
되돌리고 ③ `email:verified:*` 를 직접 넣어 인증을 건너뛴다.

⚠️ **키 접두사가 코드 여섯 곳에 흩어져 있다** (논리적으로는 셋).
`JwtAuthenticationFilter:31` 의 주석이 *「AuthServiceImpl과 동일하게 유지」* 라고
위험을 인정만 하고 넘어간다 — 한쪽만 바뀌면 **컴파일도 검사도 통과하는데 로그아웃만
조용히 무력화**된다.

---

## §6. 환경변수

🔴 **키 목록의 진실은 [`../.env.example`](../.env.example) 이다.** 여기서는 성격만 적는다.

| 그룹 | 읽는 쪽 | 비고 |
| --- | --- | --- |
| `DB_USERNAME` `DB_PASSWORD` | Spring | Oracle 접속 |
| `JWT_SECRET` | **Spring + FastAPI** | 🔴 두 서버가 같은 값이어야 한다. 다르면 AI 기능만 401 |
| `GOOGLE_CLIENT_ID` `GOOGLE_SECRET_KEY` | Spring | OAuth2 |
| `GOOGLE_EMAIL` `GOOGLE_EMAIL_SECRET_KEY` | Spring | Gmail 앱 비밀번호 |
| `GEMINI_API_KEY` | FastAPI | |
| `SUPABASE_PROJECT_URL` `SUPABASE_PUBLISHABLE_SECRET_KEY` | FastAPI | 🔴 **필수로 선언돼 있는데 읽는 코드가 없다** (Phase 3) |
| `DATA_PROBLEM_DIR` `DATA_ANSWER_DIR` | Spring | 기출 적재 경로 |
| `REDIS_HOST` `REDIS_PORT` | 양쪽 | 🔴 **비워 둔다** — `.env.example` §5 참고 |

🔴 **`.env` 자체는 Claude 가 쓰지 않는다.** 새 키가 필요하면 사용자에게 요청한다.

⚠️ **한 `.env` 를 두 서버가 읽는다.** Spring 은 `springboot4-dotenv` 로, FastAPI 는
compose 의 `env_file` 로. 그래서 **같은 이름인데 두 서버에 필요한 값이 다른 키**가 생긴다.
Redis 가 그 경우다.

---

## §7. 아직 안 정한 것 — 🔴 혼자 정하지 말 것

| 정할 것 | 언제 |
| --- | --- |
| RAG 벡터 저장소 스키마 (테이블·차원·메타데이터) | Phase 3 착수 시 |
| 스키마 마이그레이션 도구 (지금은 `ddl-auto: update`) | 운영 데이터가 생기기 전 |
| `PROBLEMS` 에 원본 `id` 컬럼을 추가할지 | 적재 멱등성을 손볼 때 |
| AI 생성 문제의 검수 상태 컬럼 | 검수 절차가 정해진 뒤 |

---

## 참고

- 스코프의 진실: [`PRD.md`](PRD.md)
- 🔴 채점·난이도 규율: [`rules/grading-and-difficulty.md`](rules/grading-and-difficulty.md)
- 엔드포인트 계약: [`API_CONTRACT.md`](API_CONTRACT.md)
- 구조 개관: [`00_CODE_WALKTHROUGH.md`](00_CODE_WALKTHROUGH.md)
