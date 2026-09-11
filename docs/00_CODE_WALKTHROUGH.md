# 코드 개관 — 어디에 무엇이 있고, 왜 그 폴더가 있나

> **최종 갱신: 2026-09-11**
> 파일이 생기거나 역할이 바뀌면 이 문서를 **같은 커밋에서** 갱신한다 — 폴더 지도, 역할 설명,
> 그리고 위의 「최종 갱신」 날짜까지. 낡은 지도는 없는 지도보다 나쁘다.

## §0. 이 문서를 처음 보는 사람에게

| 말 | 뜻 |
| --- | --- |
| **도메인** | 「회원」·「챕터」·「문제」처럼 업무 단위 하나. 폴더 하나가 도메인 하나다. |
| **컨트롤러 / 서비스 / 레포지토리** | 요청을 받는 자리 / 판단하는 자리 / DB 에 말 거는 자리. 셋이 이 순서로만 부른다. |
| **엔티티** | DB 표 한 줄에 대응하는 자바 객체. |
| **DTO** | 바깥과 주고받는 전용 객체. 엔티티를 그대로 내보내지 않으려고 둔다. |
| **필터** | 컨트롤러에 닿기 **전에** 모든 요청이 통과하는 관문. 여기서 토큰을 본다. |
| **적재(DataLoader)** | 앱이 뜰 때 외부 JSON 파일을 읽어 DB 를 채우는 일회성 작업. |

---

## §1. 전체 흐름

이 프로젝트가 하는 일은 한 줄로 이렇다.

```
학생 수준에 맞는 기출문제를 골라 주고 → 채점하고 → 그 결과로 다음 난이도를 정하고
                                                    → 관련 개념을 AI 가 설명한다
```

### 서버가 둘인 이유

```
   브라우저
      │  쿠키(accessToken)
      ▼
┌─────────────────────────┐        ┌──────────────────────────┐
│  Spring Boot  :8080     │        │  FastAPI  :8001          │
│                         │        │                          │
│  누가 학생인가 (인증)      │ 헤더    │  Gemini 에 무엇을 물을까  │
│  무엇을 낼까 (문제 선택)   │ Bearer │  (프롬프트 조립)          │
│  맞았나 (채점)           │ ─────▶ │                          │
│  다음은 뭘까 (난이도)      │        │  대화 이력 (Redis)        │
└───────────┬─────────────┘        └────────────┬─────────────┘
            │                                   │
     Oracle · Redis                         Gemini API
```

🎯 **경계선은 「우리 데이터를 아는가」다.** Spring 만 Oracle 을 안다. FastAPI 는 학생이
누군지도, 문제가 몇 개인지도 모른다 — 받은 문자열로 프롬프트를 만들 뿐이다.
왜 이렇게 나눴는지: [`rules/stack-decision.md`](rules/stack-decision.md)

### 요청 하나가 지나가는 길 — 「맞춤 문제 주세요」

```
GET /api/problems/adaptive?chapterId=1
   │
   ├─ JwtAuthenticationFilter      쿠키에서 토큰을 꺼내 확인하고
   │                               ⚠️ 로그아웃된 토큰인지 Redis 에 물어본다
   ├─ ProblemController            요청을 받아 유저를 찾는다
   ├─ ProblemServiceImpl           그 학생의 그 챕터 통계를 본다
   │                               → 없으면 「중」, 있으면 현재 난이도
   ├─ ProblemRepository            그 난이도 + SOURCE_TYPE='REAL' 로 랜덤 1건
   │                               🔴 'REAL' 을 빼면 AI 생성 문제가 섞여 나온다
   └─ ProblemResponse.of()         정답·해설을 뺀 채로 봉투에 담아 돌려준다
```

### 「답 냈어요」 — 여기가 이 프로젝트의 심장

```
POST /api/problems/42/submit
   │
   ├─ 객관식이면  → AnswerNormalizer 로 표기를 맞춰 비교      (서버가 채점)
   │  단답형이면  → request.selfJudge          (⚠️ 없으면 400, 오답 아님)
   │
   ├─ 문제가 그 챕터에 속하나?                  아니면 400
   ├─ USER_CHAPTER_STATS 에 «잠금» 을 잡는다    ← 🔴 순서가 규칙이다. 먼저 잡는다
   │                                              없으면 별도 트랜잭션으로 만들고 다시 잡는다
   ├─ 이 문제를 낸 적 있나? (첫 제출 판정)      ← ⚠️ 반드시 잠금을 잡은 «뒤»
   ├─ USER_PROBLEM_HISTORY 에 한 건 남긴다     ← 사실이므로 언제나 남긴다
   └─ 🔴 «이 문제의 첫 제출일 때만» stats.recordAnswer(맞았나)
        누적 +1 → 정답률 계산 → 난이도 재계산
        · 3문제 미만이면 안 바꾼다 (아직 모른다)
        · 80% 이상 올린다 / 50% 미만 내린다
```

🔴 **이 흐름의 신뢰성이 제품 전체를 떠받친다.** 규율:
[`rules/grading-and-difficulty.md`](rules/grading-and-difficulty.md)

---

## §2. 폴더 지도 — Spring (`src/main/java/com/example/ailang/`)

```
domain/          업무 단위별로 나뉜다. 도메인끼리는 서비스를 통해서만 부른다
  auth/            가입·인증·로그인·토큰
  user/            유저 정보·학년 (🎯 학년을 «정하는» 유일한 자리)
  chapter/         단원 목록
  problem/         🎯 문제 선택·채점·통계·난이도 — 이 프로젝트의 심장
  chat/            AI 챗봇 (FastAPI 로 넘기기만 한다)

global/          도메인이 공유하는 것들
  config/          Spring 설정 (보안·빈)
  security/        필터·핸들러·OAuth2·유저 상세
  jwt/             토큰 만들기·읽기
  client/          🎯 FastAPI 를 부르는 유일한 자리
  redis/           Redis 접근
  mail/            메일 발송
  exception/       예외 어휘와 전역 처리
  response/        공통 응답 봉투
  entity/          공통 상위 엔티티 (생성·수정 시각)
  loader/          🎯 기출문제 적재 (앱 시작 시 1회)
```

각 도메인 안은 같은 모양이다.

| 하위 폴더 | 역할 | 🧭 왜 따로 두나 |
| --- | --- | --- |
| `controller/` | HTTP 요청을 받고 응답을 만든다 | 여기에 판단 로직을 넣으면 테스트에 서버가 필요해진다 |
| `service/` | 판단한다 (인터페이스 + `Impl`) | 🎯 **채점·난이도 같은 규칙이 사는 자리** |
| `repository/` | DB 에 말 건다 | 쿼리를 한곳에 모아 둬야 「어떤 조건으로 뽑고 있나」를 셀 수 있다 |
| `entity/` | DB 표에 대응 | |
| `dto/request/`·`dto/response/` | 바깥과 주고받는 모양 | 🔴 엔티티를 그대로 내보내면 `answer` 가 새어 나간다 |
| `enums/` | 어휘 (`Difficulty` 등) | 🔴 문자열로 손으로 적지 않기 위한 자리 |
| `exception/` | 그 도메인의 실패 종류 | |

## §3. 폴더 지도 — FastAPI (`ai-server/app/`)

```
main.py            앱 조립 + 라우터 등록 + 실패 종류별 응답 (429·502·503)
config.py          환경변수를 읽는 유일한 자리
exceptions.py      실패의 «종류» 넷 (한도·토큰·응답형식·무응답)
llm.py             🎯 모델 클라이언트를 만드는 유일한 자리 (한 번만 만든다)
llm_call.py        🎯 모델을 «부르는» 유일한 자리. 여기서 실패를 분류한다
routers/           요청/응답 모양(pydantic)과 경로만. 판단은 안 한다
  chat.py  concept.py  problem.py
services/          🎯 프롬프트가 사는 자리
  auth_service.py    JWT 검증 — SERVICE 토큰만 받는다 (학생 토큰은 401)
  concept_service.py 개념 설명 프롬프트
  problem_service.py 모의문제 프롬프트 + JSON 파싱 + LaTeX 복원 + 저장 전 검증
  rag_service.py     챗봇 — ⚠️ 이름과 달리 RAG 검색은 아직 없다 (Phase 3)
```

---

## §4. 지금 있는 파일 — 손대기 전에 알아야 할 것

### 🔴 이 프로젝트의 심장

| 파일 | 무엇 | 알아야 할 것 |
| --- | --- | --- |
| `domain/problem/service/ProblemServiceImpl` | 문제 선택·채점·개념 설명·AI 문제 | ⚠️ 단답형은 여전히 클라이언트 판정을 쓴다(결정 대기). `getAiProblem` 은 트랜잭션 밖에서 AI 를 부른다 |
| `domain/problem/entity/UserChapterStats` | 통계 누적 + 난이도 재계산 | 🎯 난이도 규칙이 **여기 하나에만** 있다. 좋은 상태다 — 흩뜨리지 말 것 |
| `domain/problem/enums/Difficulty` | `upgrade()` / `downgrade()` | 양 끝(LOW·HIGH)에서 제자리. 🔴 아직 검사가 없다 |
| `domain/problem/service/AiProblemStore` | AI 문제의 DB 작업만 | 🎯 트랜잭션을 AI 호출 앞뒤로 짧게 나누려고 뗀 별도 빈 |
| `domain/problem/service/UserChapterStatsCreator` | 통계 행을 «없으면 만드는» 일만 | 🎯 별도 트랜잭션(`REQUIRES_NEW`) 이어야 하는 것이 존재 이유다 — 같은 트랜잭션에서 유일 제약 위반을 잡으면 롤백밖에 못 한다 |
| `domain/problem/service/AnswerNormalizer` | 정답 표기 맞추기 (순수 함수) | ✅ **검사가 붙어 있다** (건수는 `TODOS.md` §5). 🔴 모르는 표기는 지우지 않는다 — 지우면 다른 답이 같아진다 |
| `domain/problem/repository/ProblemRepository` | 네이티브 쿼리 5개 | 🔴 전부 `SOURCE_TYPE = 'REAL'` 을 손으로 적는다. 새 쿼리에서 빠지면 조용히 섞인다 |

### 🔒 경계와 관문

| 파일 | 무엇 | 알아야 할 것 |
| --- | --- | --- |
| `global/config/SecurityConfig` | 경로별 인증 규칙·CORS | 🔴 **역할(ADMIN) 규칙이 하나도 없다.** `GET /api/chapters` 만 열려 있다 |
| `global/security/filter/JwtAuthenticationFilter` | 쿠키 토큰 검증 + 블랙리스트 | 🎯 토큰이 없으면 그냥 통과시킨다 — 거부는 `SecurityConfig` 몫 |
| `global/jwt/JwtTokenProvider` · `TokenType` | 토큰 발급·파싱·**종류 확인** | jjwt 0.11.2 (옛 API). 🔴 `typ` 클레임(ACCESS/REFRESH/SERVICE)이 쓰이는 자리마다 요구된다 |
| `global/client/AiServerClient` | 🎯 **FastAPI 를 부르는 유일한 자리** | 서비스 토큰을 붙이고, AI 실패를 429·502·503 으로 보존한다 |
| `ai-server/app/services/auth_service.py` | 반대편 JWT 검증 | SERVICE 토큰만 받는다. ⚠️ Spring 의 로그아웃 블랙리스트는 여전히 모른다 |

### 🔧 그 외

| 파일 | 무엇 | 알아야 할 것 |
| --- | --- | --- |
| `global/loader/DataLoader` | 기출 JSON → DB | 🔴 경로가 틀려도 **경고만** 남기고 문제 0건으로 뜬다 |
| `global/exception/GlobalExceptionHandler` | 전역 예외 → 응답 | ⚠️ 마지막 `RuntimeException` 분기가 넓다. AI 실패는 `AiServerClient` 가 먼저 분류해 빠져나간다 |
| `global/response/ResponseDTO` | 공통 봉투 | `code` 는 HTTP 상태와 같은 값이다 |
| `global/config/AppConfig` | `PasswordEncoder`·`RestTemplate` | 연결 5초·읽기 60초. ⚠️ 읽기가 긴 건 AI 응답이 실측 13~19초라서다 |
| `scripts/normalize_answers.py` | 원문자 정답 일괄 정규화 | ⚠️ **1회성**이고 DB 접속 정보가 **하드코딩**돼 있다. 돌리기 전에 확인할 것 |

---

## §5. 모듈 ↔ 근거 표

> 🧭 **「이 파일이 왜 존재하나」**에 답하는 자리다. 대부분의 모듈은 어떤 사고나 요구 때문에
> 생긴다. 그 이유를 안 적어 두면 석 달 뒤에 아무도 모른다.
> 🔴 **비워 두지 말 것** — 파일이 생길 때 같은 커밋에서 한 줄 적는다.

| 모듈 | 왜 생겼나 |
| --- | --- |
| `Problem.sourceType` | Gemini 생성 문제를 저장하기 시작하면서, 기출과 섞이면 **검증 안 된 정답이 학생의 난이도를 정하게 되므로** 분리했다 |
| `AnswerNormalizer` | 적재된 기출 정답이 LaTeX(`$\frac{3}{4}$`)·원문자(`①`)로 들어 있어 학생 입력과 문자열 비교가 안 됐다. 🔄 2026-09-02 에 `ProblemServiceImpl` 의 private 메서드에서 꺼냈다 — private 이라 검사를 못 붙이고 있었다 |
| `TokenType` | 액세스·리프레시·서비스 토큰이 구분되지 않아, 리프레시 토큰 하나로 모든 API 가 통과했다 |
| `AiProblemStore` | AI 호출이 실측 13초인데 트랜잭션 안에 있어 그동안 DB 커넥션을 붙잡았다 |
| `UserChapterStatsCreator` | 같은 학생의 동시 제출이 통계 행을 둘 다 만들어 유일 제약을 깨고 **답을 냈는데 500** 이 났다. 제약 위반을 잡아 회복하려면 삽입이 별도 트랜잭션이어야 한다 (2026-09-11) |
| `ai-server/app/llm_call.py` | 실패를 세 서비스가 각자 분류하고 있었다. 한 자리로 모았다 |
| `scripts/normalize_answers.py` | 위 정규화를 런타임이 아니라 **저장된 데이터 자체**에 한 번 적용하려고. 정규식으로 못 푸는 정답이 남아 있었다 |
| `AiServerClient.withAuth` | FastAPI 호출에 JWT 가 빠져 401 이 나던 것을 고치면서. ⚠️ 그때 「서비스 신원」이 아니라 **가짜 유저 토큰**(`service@internal`)으로 때웠고, 그래서 학생 토큰과 구분되지 않았다. 🔄 2026-09-01 에 `TokenType.SERVICE` 로 정리했다 |
| `JwtAuthenticationFilter` 의 블랙리스트 | 로그아웃 뒤에도 탈취된 access token 이 만료 전까지 유효하던 취약점 때문 |
| `User.assessmentCompleted` | 진단 테스트를 이미 본 학생에게 또 보여주지 않으려고 |
| `UserGrades` | `user.getGrade().name()` 이 다섯 자리에 흩어져 있었고, 학년 없는 계정(구글 가입)이 그 다섯 곳에서 전부 **NPE → 500** 이었다. 자리마다 막으면 여섯 번째가 또 빠진다 |
| `UpdateGradeRequest` + `PATCH /me/grade` | 위 `UserGrades` 가 「학년을 먼저 설정해 주세요」(400)를 내기 시작했는데, **정작 설정할 길이 없었다.** 구글 가입 학생은 안내를 받고도 갈 곳이 없었다 (2026-09-11) |
| `frontend/src/constants/grades.ts` | 같은 학년 목록이 가입 화면·홈 화면에 따로 적혀 있어, 학년 설정 화면을 만들면 **네 번째 사본**이 될 참이었다 |
| `springboot4-dotenv` 의존성 | Spring Boot 4 에서 `spring-dotenv` 4.0.0 이 안 붙어서. `build.gradle` 주석에 근거가 있다 |
| `jackson-databind` 명시 의존성 | Spring Boot 4 의 `starter-web` 이 더 이상 전이 포함하지 않아 컴파일이 깨졌다 |

---

## §6. 실행

```bash
cp .env.example .env          # 값은 직접 채운다
docker compose up -d          # Oracle · Redis · FastAPI
./gradlew bootRun             # Spring (compose 에 없다 — 따로 띄운다)
./gradlew test                # 🎯 인프라 없이 도는 검사 15건 + 컨텍스트 로딩 1건
```

🔴 **기동 로그에서 `[DataLoader] 적재 완료 - 삽입: N개` 의 N 을 확인할 것.**
경로가 틀리면 에러 없이 0건으로 뜬다.

전체 실행 순서와 주의: [`../README.md`](../README.md) § 실행
