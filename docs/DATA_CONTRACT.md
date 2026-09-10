# 데이터 계약 — 표·컬럼·어휘·환경변수의 진실

> **최종 갱신: 2026-09-02** — 코드에서 확인한 상태다.
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
| `TITLE` | VARCHAR2(100) NOT NULL | 🔄 **대단원명**. 「소인수분해」, 「기본 도형」 |
| `DESCRIPTION` | VARCHAR2(500) | 🔄 「1학기 · 도형과 측정」. 원본의 학기·영역만 쓴다 |
| `ORDER_NUM` | NUMBER NOT NULL | 같은 학년 안에서의 표시 순서 = `question_unit`(1~8) |

🔄 **2026-09-09: 챕터는 «유형» 이 아니라 «대단원» 이다.** 예전에는 원본의
`question_topic_name`(「맞꼭지각(1)」 같은 문제 유형)을 그대로 챕터로 써서 **331개**가 됐고,
그중 **200개(60%)가 문제 3개 이하**였다. 난이도 조정은 **챕터당 3문제 이상**이라야
시작하므로 **그 챕터들은 난이도가 영원히 안 움직였다.**

이제 `question_unit`(01~08)으로 묶어 **중1 기준 챕터 8개 · 챕터당 76~246문제**다.
유형은 버리지 않고 `PROBLEMS.TOPIC` 으로 옮겼다.
근거: [`research/chapter-granularity-2026-09-09.md`](research/chapter-granularity-2026-09-09.md)

🔄 **`(GRADE, TITLE)` 에 유니크 제약을 걸었다** (`UK_CHAPTERS_GRADE_TITLE`).
적재는 조회 후 없으면 생성(read-then-write)이라 코드만으로는 중복을 못 막는다 —
챕터가 갈리면 문제도 통계도 갈린다.

🔄 **`ORDER_NUM` 이 이제 실제로 순서다.** 챕터 8개에 순번 1~8 이 하나씩 대응한다.
예전에는 챕터 331개가 순번 8종을 나눠 가져 한 순번에 수십 개가 몰렸다.

### PROBLEMS

| 컬럼 | 타입 | 비고 |
| --- | --- | --- |
| `ID` | NUMBER (PK) | `PROBLEMS_SEQ` |
| `CHAPTER_ID` | NUMBER NOT NULL (FK) | 🎯 문제는 학년을 직접 모른다 — 챕터를 타고 간다 |
| `DIFFICULTY` | VARCHAR2(10) NOT NULL | `LOW` / `MEDIUM` / `HIGH` |
| `PROBLEM_TYPE` | VARCHAR2(20) NOT NULL | `MULTIPLE_CHOICE` / `SHORT_ANSWER` |
| `QUESTION` | CLOB NOT NULL | 문제 본문 |
| `OPTIONS` | VARCHAR2(**1000**) | 객관식 보기 JSON 배열. 단답형이면 null |
| `ANSWER` | VARCHAR2(200) NOT NULL | 🔴 절대 제출 전에 응답에 실리지 않는다 · 🔴 **폭이 모자란다 — 아래** |
| `EXPLANATION` | CLOB NOT NULL | 해설 |
| `SOURCE_TYPE` | VARCHAR2(10) NOT NULL DEFAULT `REAL` | 🔴 `REAL`(기출) / `AI`(생성) |
| `TOPIC` | VARCHAR2(200) | 🔄 **신설.** 문제 유형명(「맞꼭지각(1)」). AI 생성이면 null |
| `SOURCE_ID` | VARCHAR2(50) UNIQUE | 🔄 **신설.** 원본 AI Hub 문제 id. AI 생성이면 null |

🔄 **2026-09-09: `TOPIC` 과 `SOURCE_ID` 를 넣었다.**
- `TOPIC` — 챕터가 유형 331개에서 대단원 8개로 넓어졌다. 유형을 여기 안 남기면 331종의
  정보가 통째로 사라진다. 🎯 개념 설명 요청에 **`단원 · 유형` 을 함께** 보내야 예전과
  같은 정확도가 나온다 — 챕터명만 보내면 「기본 도형」처럼 뭉뚱그려진다.
- `SOURCE_ID` — 이게 없어서 적재가 **전부 아니면 전무**였다. 절반만 들어간 상태에서
  나머지를 채우거나 새 학년 자료를 덧붙일 수 없었다. `UNIQUE` 라 같은 원본이 두 번
  들어오지 않는다 (Oracle 은 `NULL` 을 여러 개 허용하므로 AI 문제는 걸리지 않는다).

🔴 **`ANSWER` 의 200자는 «기출문제에서 이미» 넘치고 있다** (📏 2026-09-10 실측).
원본 `answer_bbox` 의 `type=="answer"` 칸에 **정답과 풀이가 함께** 들어 있는 건이 있어,
정답 글자수가 213~1,106자가 된다. 그 **20건은 저장에서 터져 조용히 버려진다** —
저장된 1,132건의 최대가 190자이고 빠진 20건의 최소가 213자로 **정확히 갈린다.**
⚠️ 빠지는 것이 대부분 **서술형·표가 있는 문제**라 자료가 치우친다.
🧭 어떻게 담을지는 채점 방식과 얽힌 **사용자 결정**이다
([`research/reload-result-2026-09-10.md`](research/reload-result-2026-09-10.md) §2).

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
| `IS_CORRECT` | NUMBER(1) NOT NULL | 객관식은 서버 채점, 단답형은 학생 자가 채점. ⚠️ 후자는 결정 대기 |
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
| 단답형인데 `selfJudge` 가 안 왔다 | ✅ **400 으로 거부** | (닫힘) |
| 그 챕터를 아직 3문제 미만 풀었다 | 난이도 유지 ✅ | (맞다) 「모른다」로 취급 중 |
| 학년을 아직 안 골랐다 (구글 가입) | — | 🔴 NPE 로 500. 「미입력」 상태가 필요하다 |
| AI 호출이 실패했다 | ✅ 429·502·503 으로 구분 | (닫힘) |
| 적재 때 건너뛴 문제 | ✅ 사유 6종으로 집계 (§5) | (닫힘) |

🔴 **세 가지 상태가 필요한 자리에 boolean 을 쓰지 않는다.**
「맞음/틀림」에는 언제나 「아직 안 풀었음」이 따라온다.

---

## §5. 적재(DataLoader) 계약

원본 자료 구조: [`research/aihub-json-structure.md`](research/aihub-json-structure.md)

| 원본 | → | 우리 값 | 규칙 |
| --- | --- | --- | --- |
| `question_grade` | → | `Grade` | `E3`~`E6`→`ELEM_*`, `M1`~`M3`→`MIDDLE_*`, `H1`→`HIGH_1`. 모르면 건너뜀 |
| **`question_unit`** | → | `CHAPTERS` | 🔄 **챕터는 대단원이다.** 「학년코드-단원번호」로 묶는다 (`M1-03`) |
| `data/chapter-titles.csv` | → | `CHAPTERS.TITLE` | 🔄 표에서 이름을 가져온다. 없으면 「(이름 미등록) NN단원」 + 로그 |
| `question_term`·`question_sector2` | → | `CHAPTERS.DESCRIPTION` | 🔄 「1학기 · 도형과 측정」. 둘 다 없으면 null |
| `question_unit` | → | `CHAPTERS.ORDER_NUM` | 숫자가 아니면 맨 뒤로 보낸다(문제는 살린다) |
| `question_topic_name` | → | `PROBLEMS.TOPIC` | 🔄 **유형명.** 예전에는 이 값이 챕터 이름이었다 |
| `id` | → | `PROBLEMS.SOURCE_ID` | 🔄 원본 AI Hub id |
| **`id`** | → | `Difficulty` | 🔄 **먼저 본다.** `data/difficulty-overrides.csv` 에 그 id 가 있으면 그 값을 쓴다 · 🔴 아래 |
| `question_step` | → | `Difficulty` | 위 표에 없을 때만. `기본`→LOW, `표준`→MEDIUM, `심화`→HIGH |
| `question_difficulty` (1~5) | → | `Difficulty` | 위 둘 다 없을 때만. ≤2 LOW, ≤3 MEDIUM, 그 외 HIGH |
| `question_type1` | → | `ProblemType` | `선택형`→MULTIPLE_CHOICE, 그 외 SHORT_ANSWER |
| `OCR_info[0].question_text` | → | `QUESTION` | 보기가 이 안에 들어 있다 |
| `answer_bbox` 중 `type=="answer"` | → | `ANSWER` | 🔄 **전부** 쉼표로 이어 붙인다(복수정답 6건). 비면 건너뜀 |
| `answer_info[0].answer_text` | → | `EXPLANATION` | |
| (고정) | → | `SOURCE_TYPE` | 항상 `REAL` |

### 🔴 난이도는 원본에서 «유도되지 않는다» — 그래서 기록한다

원본의 `question_step` 값은 **`기본`(935) · `실생활응용`(217) 둘뿐**이라 `표준`·`심화`
두 줄은 한 번도 안 쓰이고, `question_difficulty` 최댓값이 **3** 이라 `> 3` 조건인 HIGH 도
안 나온다. 2026-09-07 에 `question_info` 의 **12개 필드 전부와 그 조합**을 DB 난이도와
대조했는데 어느 것도 설명하지 못했다 (최고 `question_unit` 86.4%, 그마저 단원 안의
분화는 **모든 필드가 다수결 기준선과 정확히 같아** 정보량이 0 이었다).

🎯 **유도할 수 없다고 재현까지 못 하는 것은 아니다.** 「원본 id → 난이도」를
`src/main/resources/data/difficulty-overrides.csv` 에 1,132줄로 **기록**하고,
적재할 때 이 표를 **가장 먼저** 본다.

| | 하 | 중 | 상 | 지금 DB 와 일치 |
| --- | --- | --- | --- | --- |
| 지금 DB | 454 | 432 | 246 | — |
| **스냅샷 사용 (현재 동작)** | 472 | 436 | 244 | **99.8%** (1130/1132) |
| 스냅샷 없이 원본 필드만 | **1,131** | **21** | **0** | 41.0% |

못 맞히는 2건은 **본문이 완전히 같은데 난이도가 다른** 문제다 — 구분하는 것이 그림이라
텍스트로는 짚을 수 없다.

🔴 **이 파일은 «규칙» 이 아니라 «기록» 이다.** 새 학년 자료에는 그 id 가 없어 아래
매핑으로 떨어지고, 그러면 **상(HIGH) 이 한 건도 안 나온다.** 적재 로그가 몇 건이
스냅샷에서·몇 건이 매핑에서 왔는지 알려 준다.
파일이 아예 없어도 적재는 돈다(경고만 남는다).

전말·후보 비교: [`research/difficulty-origin-2026-09-07.md`](research/difficulty-origin-2026-09-07.md)

**멱등성**: 보호는 `countBySourceType(REAL) > 0` 이다.
🔄 **2026-09-02 정정.** 예전에는 전체 건수(`count()`)를 봤는데, AI 모의문제가 같은 표에
저장되므로 **AI 문제 한 건만 생겨도 기출 적재를 영원히 건너뛰었다.**

🔴 **여전히 전부 아니면 전무다.** 절반만 들어간 상태에서 나머지를 채울 수 없고, 새 학년
자료를 추가할 수도 없다. 진짜 키는 AI Hub 의 `id` 인데 `PROBLEMS` 에 그 컬럼이 없다
([`../TODOS.md`](../TODOS.md) 참고).

### 🔴 재적재 절차 — 챕터 기준이 바뀌면 반드시 거쳐야 한다

챕터 기준을 바꾸면 기존 `CHAPTERS` 331행과 그것을 가리키는 모든 행이 **뜻을 잃는다.**
`DataLoader` 는 기출문제가 이미 있으면 건너뛰므로, **표를 비워야 새 기준이 적용된다.**

```sql
-- ⚠️ 지우는 순서가 중요하다 (자식 → 부모). 실행 전에 무엇이 사라지는지 세어 볼 것.
SELECT COUNT(*) FROM USER_PROBLEM_HISTORY;   -- 학생 풀이 이력
SELECT COUNT(*) FROM USER_CHAPTER_STATS;     -- 챕터별 정답률·현재 난이도

DELETE FROM USER_PROBLEM_HISTORY;
DELETE FROM USER_CHAPTER_STATS;
DELETE FROM PROBLEMS;
DELETE FROM CHAPTERS;
COMMIT;
```

🔴 **학생의 학습 기록이 사라진다.** 챕터가 달라지면 「어느 챕터에서 몇 문제를 맞혔나」가
가리킬 곳을 잃기 때문에, 남겨 두면 통계가 **틀린 챕터에 붙는다.**
⚠️ 운영 데이터가 생긴 뒤에는 이 방법을 쓸 수 없다 — 마이그레이션 도구가 필요하다
(TODOS 8절 「스키마 마이그레이션 도구」).

지운 뒤 앱을 다시 띄우면 적재가 돌고, 로그에 챕터 수·난이도 출처·이름 못 찾은 단원이 남는다.

**실패 분류**: 🔄 사유 6종으로 나눈다 (`LoadOutcome`) — 저장 · 답안 파일 없음 ·
정답 자리 비어 있음 · 처음 보는 학년 코드 · 필수 필드 없음 · 처리 중 오류.
예전에는 전부 `skipped` 하나였고, 그러면 「몇 건 안 들어왔다」는 알아도 **무엇을 고쳐야
하는지**는 알 수 없었다.

**결정성**: 파일을 **이름순**으로 읽는다. `listFiles()` 순서가 OS 의존인데 챕터의
`ORDER_NUM` 이 「먼저 만난 파일」로 정해져서, 예전에는 같은 자료로도 PC 마다 결과가 달랐다.

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
