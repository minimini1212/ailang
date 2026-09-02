# ailang — AI와 함께하는 수학 공부

> **상태:** 🟡 **Phase 2 완료 · Phase 3(RAG) 미착수** · 2026-09-02 기준
>
> 🔴 **가장 큰 미결: 난이도 축이 재현되지 않는다.** 지금 DB 에는 하454·중432·상246 이
> 들어 있는데, 같은 원본 자료를 현재 코드로 다시 적재하면 **상이 0건**이 된다.
> DB 를 비우면 되돌릴 수 없다.
> → [docs/research/aihub-data-measurement-2026-08-31.md](docs/research/aihub-data-measurement-2026-08-31.md)

## 개요

초등 3학년 ~ 고등 1학년 학생에게 **자기 수준에 맞는 수학 문제**를 내주고, 푼 직후에
**그 문제와 연결된 개념을 학년 눈높이로 설명**해 주는 학습 플랫폼이다.

문제는 **AI Hub 수학 자료(기출)** 를 적재해서 쓰고, 개념 설명·모의문제·챗봇은 **Gemini** 가
만든다. 이 둘은 절대 섞이지 않는다 — [§ 기출과 AI 생성은 다른 것](#기출과-ai-생성은-다른-것).

```
학생이 챕터를 고른다
     ↓
그 학생의 그 챕터 정답률을 본다        ← 처음이면 「중」에서 시작
     ↓
정답률에 맞는 난이도의 기출문제 1개를 준다
     ↓
답을 제출하면 채점 + 해설 + 통계 갱신 + 난이도 재계산
     ↓
「개념 설명 보기」를 누르면 Gemini 가 그 학년 수준으로 설명한다
```

### 기출과 AI 생성은 다른 것

🔴 **이 프로젝트에서 가장 중요한 한 줄:**
> **Gemini 가 만든 문제를 기출문제로 저장해서는 안 된다.**

둘 다 같은 `PROBLEMS` 표에 들어가지만 `SOURCE_TYPE` 으로 갈린다(`REAL` / `AI`).
AI 가 만든 문제는 **정답이 검증되지 않았다.** 그걸 진단 테스트나 맞춤 문제에 섞으면,
학생의 난이도를 정하는 그 정답률이 검증 안 된 정답으로 계산된다.

### 난이도는 이렇게 움직인다

| 조건 | 결과 |
| --- | --- |
| 그 챕터를 3문제 미만 풀었다 | **바꾸지 않는다** — 아직 모른다 |
| 정답률 80% 이상 | 한 단계 올린다 (하→중→상) |
| 정답률 50% 미만 | 한 단계 내린다 (상→중→하) |
| 그 사이 | 유지 |

⚠️ 「3문제 미만」은 「이 학생은 못한다」가 아니라 **「아직 모른다」**이다.
자세한 규율: [docs/rules/grading-and-difficulty.md](docs/rules/grading-and-difficulty.md)

## 구조 — 서버가 둘, 저장소가 셋

```
브라우저 (5173)
     │
     ▼
Spring Boot 8080 ───HTTP(JWT)──▶ FastAPI 8001
  인증 · 문제 · 통계                개념 설명 · 모의문제 · 챗봇
     │                                    │
     ├── Oracle 23ai 1521                 ├── Gemini (gemini-2.5-flash)
     │     유저 · 챕터 · 문제 · 통계        └── Redis  대화 이력 (1시간)
     │
     └── Redis 6380
           이메일 인증코드 · 로그아웃 토큰 블랙리스트
```

두 서버는 **JWT 시크릿 하나와 HTTP 계약**만 공유한다. 서로의 코드를 import 하지 않고,
서로의 DB 에 닿지 않는다. 왜 이렇게 나눴는지: [docs/rules/stack-decision.md](docs/rules/stack-decision.md)

## 지금 되는 것 / 안 되는 것 (2026-09-02)

| | 상태 |
| --- | --- |
| | 백엔드 | 화면 |
| --- | --- | --- |
| 회원가입 · 이메일 인증 · 로그인 · 구글 OAuth2 · 토큰 재발급 | ✅ | ✅ |
| 챕터 목록 (학년별 + 내 통계) | ✅ | ✅ |
| 진단 테스트 20문제 | ✅ | ✅ |
| 답안 제출 · 통계 갱신 · 난이도 재계산 | ✅ | (진단에서만) |
| **맞춤 문제 · 랜덤 문제 · 학년별 랜덤** | ✅ | 🔴 **없음** |
| **개념 설명 (Gemini)** | ✅ | 🔴 **없음** |
| **AI 모의문제 생성 (Gemini)** | ✅ | 🔴 **없음** |
| AI 챗봇 | ✅ | ✅ |
| **RAG (Supabase pgvector 검색)** | ⬜ **코드 0줄** — 라이브러리만 설치돼 있다 |
| 관리자 API (챕터·문제 CRUD) | ⬜ 없다 |
| 자동화 검사 | 🟡 **채점 로직 15건.** 난이도 전이·적재는 아직 없다 |

## 지금 할 일

🔴 **RAG·관리자 API·새 화면보다 먼저 막아야 할 것이 있다.**
2026-08-27 리뷰 두 건의 결론이다 ([`docs/review/`](docs/review/)).

| # | 무엇 | 왜 먼저인가 |
| --- | --- | --- |
| 0 | **재기부터** — 실제 LaTeX 정답 건수·적재된 문제 수·학년별 건수 | 코드 0줄로 아래 순위가 정해진다 |
| 1 | 토큰에 종류(`typ`) 클레임 | 리프레시 토큰이 **7일짜리 전체 API 자격증명**이고 로그아웃이 못 막는다 |
| 2 | 챗봇에 학생 신원 전달 (**Spring 부터**) | 지금 신원이 아예 안 간다 — FastAPI 만 고치면 안 닫힌다 |
| 3 | Redis 비밀번호·바인딩 | 그 안에 리프레시 토큰 원문·로그아웃 근거가 있다 |
| 4 | ~~정답 정규화 충돌~~ | ✅ 닫힘 — `AnswerNormalizer` 로 꺼내고 검사 15건 |
| 5 | 정답 공개를 단답형으로 제한 · `selfJudge` 누락 400 · 소속 검증 · 반복 제출 | 정답률 고리를 닫는다 |
| 6 | AI 호출 타임아웃 · 트랜잭션 분리 · 429 보존 | AI 서버가 느려지면 스프링이 같이 멈춘다 |
| 7 | `DataLoader` 가드를 `REAL` 기준으로 | AI 문제 1건만 생겨도 **기출이 영영 안 들어온다** |
| 8 | 채점·난이도 로직 검사 붙이기 | 지금은 「맞게 동작한다」는 근거가 없다 |

전체 목록과 근거: [TODOS.md](TODOS.md)

## 문서 지도

| 문서 | 내용 |
| --- | --- |
| [CLAUDE.md](CLAUDE.md) | 상시 규율·가드레일 (세션 시작 시 자동 로드) |
| **[docs/rules/grading-and-difficulty.md](docs/rules/grading-and-difficulty.md)** | 🔴 **채점·난이도 건드리기 전 필독** — 누가 정답을 정하는가 |
| **[docs/rules/ai-call-policy.md](docs/rules/ai-call-policy.md)** | 🔴 **AI 호출 코드 쓰기 전 필독** |
| [docs/PRD.md](docs/PRD.md) | 스코프의 진실 — 무엇을 왜 만드는가 |
| [docs/DATA_CONTRACT.md](docs/DATA_CONTRACT.md) | 표·컬럼·enum·환경변수의 진실 (코드 작성 전 필독) |
| [docs/API_CONTRACT.md](docs/API_CONTRACT.md) | 엔드포인트가 무엇을 받고 무엇을 주는가 |
| [docs/00_CODE_WALKTHROUGH.md](docs/00_CODE_WALKTHROUGH.md) | 전체 구조·폴더 역할 — 여기부터 읽으면 그림이 잡힌다 |
| [docs/rules/](docs/rules/) | 규율 모음집 (상세 정책·의사결정 기록) |
| [docs/research/](docs/research/) | 조사 원자료 (AI Hub 자료 구조 등) |
| [docs/reports/daily/](docs/reports/daily/) | 일일 진행 기록 — 그날 무엇을·왜·어디까지 |
| [docs/reports/mentor/](docs/reports/mentor/) | 멘토 보고용 정리본 |
| [docs/review/](docs/review/) | 리뷰 산출물 — 🔴 지적 표는 그날의 사진이지 남은 일 목록이 아니다 |
| [TODOS.md](TODOS.md) | 남은 일 정본 |

🎯 **폴더마다 `README.md` 가 「여기 무엇을 두고 무엇을 두지 말 것인가」를 적어 두었다.**
파일을 추가하기 전에 그 README 를 먼저 읽는다 — 이 갈래를 나눠 둔 것이 요점이라,
엉뚱한 곳에 넣은 기록은 안 쓴 것과 같다.

## 실행

### 1. 환경변수

```bash
cp .env.example .env      # 값은 직접 채운다. .env 는 커밋하지 않는다
```

키 목록의 진실은 [.env.example](.env.example) 이다.

### 2. 인프라 (Oracle · Redis · AI 서버)

```bash
docker compose up -d
```

⚠️ **스프링은 이 compose 에 없다.** 로컬에서 따로 띄운다 (아래 3번).
Oracle 은 첫 기동에 수 분 걸린다 — `docker logs -f oracle23ai` 로 준비 완료를 확인한다.

### 3. Spring Boot

```bash
./gradlew bootRun         # Windows: gradlew.bat bootRun
```

첫 기동 때 `DataLoader` 가 AI Hub JSON 을 읽어 `PROBLEMS` 표를 채운다.
🔴 **경로를 `.env` 에 넣지 않으면 문제 0건으로 뜬다.** 에러가 아니라 경고 로그만 남으므로
`[DataLoader] 적재 완료 - 삽입: N개` 줄의 N 을 반드시 확인한다.

### 4. 검사

```bash
./gradlew test            # 🎯 인프라 없이 도는 검사 15건 (채점 정규화)
```

Java 21 · Spring Boot 4.0.2 · Gradle · Python 3.11 · FastAPI.
스택 선택 근거: [docs/rules/stack-decision.md](docs/rules/stack-decision.md)

## 참고

- 문서·규율 체계는 **`cookiedeal` 모노레포의 `apps/commerce-signal`** 관례를 이식했다.
  코드는 공유하지 않는다 — 이식한 것은 「무엇을 어디에 적는가」와 「무엇을 하면 안 되는가」다.
- `PROJECT_PLANNER.md` 는 [docs/PRD.md](docs/PRD.md) 로 옮겼다. 한 파일이 스코프·ERD·API 명세·
  진행 상황을 전부 들고 있어서 어느 한 곳이 바뀌면 나머지가 조용히 낡았다
  (실제로 「Spring Boot 3.x」·「Gemini 1.5 Flash」로 몇 달간 틀린 채 남아 있었다).
  지금은 스코프는 `PRD.md`, 표·컬럼은 `DATA_CONTRACT.md`, 엔드포인트는 `API_CONTRACT.md`,
  남은 일은 `TODOS.md` 가 각각 맡는다.
