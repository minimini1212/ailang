# TODOS — 남은 일 정본

> **이 파일에는 「남은 것」만 적는다.** 무엇을 했나·무엇을 쟀나·왜 깨졌나는
> `docs/reports/daily/` 로 간다. 끝난 항목은 주석을 붙이는 것이 아니라 **한 줄로 접어**
> 맨 아래 `✅ 끝난 것` 표로 옮기고, 상세는 정본 문서로 링크한다.
>
> 🔴 **체크 안 된 상자는 근거가 아니다.** `[ ]` 를 보고 일을 시작하기 전에 코드로 확인할 것.
> `PROJECT_PLANNER.md` 는 Phase 2 를 완료로 표시해 두고, 실제로 머지돼 있던 정답 공개 API·
> 자가채점·진단 테스트·AI 모의문제는 아예 언급하지 않았다.
>
> ⚠️ **800줄이 신호다.** 그보다 길어졌다면 이야기가 새어 들어온 것이니 덧붙이기 전에 쳐낸다.
> 지운 것은 `git log -- TODOS.md` 로 되살릴 수 있다.

---

## 🚦 지금 어디에 있나

**기능은 Phase 2 까지 돌아간다. 그런데 그 기능의 근거가 되는 숫자를 믿을 수 없다.**

정답률이 난이도를 정하고, 난이도가 다음 문제를 정한다. 그 정답률을 지금은 **클라이언트가
정한다.** 그래서 다음 할 일은 새 기능(RAG·관리자 API)이 아니라 **1절과 2절**이다.

🔴 **1절·2절을 끝내기 전에 Phase 3(RAG)에 손대지 않는다.** RAG 를 붙이면 코드가 늘고,
늘어난 코드 위에서 같은 결함을 고치는 것이 더 비싸다.

### 🎯 화면이 아직 「고리」를 안 열었다 (2026-08-27 프론트 확인)

프론트엔드는 `C:\webStorm_workspace\ailang` 에 있다(별도 폴더, git 저장소 아님).
화면이 다섯 개뿐이고, **맞춤 문제·개념 설명·AI 모의문제 화면이 없다** —
`/adaptive` · `/random` · `/random-by-grade` · `/{id}/concept` · `/ai-generated`
**다섯 엔드포인트가 아무 데서도 안 불린다.**

학생이 문제를 푸는 유일한 경로는 **진단 테스트 20문제**(`Assessment.tsx`)다.

- 🎯 **그래서 지금이 고칠 때다.** 화면을 붙이고 나면 조작 가능한 고리가 열린 채로 커진다.
- ⚠️ 다만 진단 테스트도 `submitAnswer` 를 부르므로 **통계는 이미 갱신되고 있다.**
  20문제가 여러 챕터에 흩어져 각 챕터에 1~2건씩만 남고, `totalCount < 3` 이라 난이도는
  안 움직이면서 **정답률 분모만 오염된다.** 진단 결과를 초기 난이도로 쓸지는 8절 참고.

### 순서

```
0. 측정 (코드 0줄)   실제 LaTeX 정답 건수 · 적재된 문제 수 · 학년별 건수
1. 인증 경계         typ 클레임 · 챗봇 신원 · Redis · 로그아웃 · 인증코드
2. 채점 정확성       normalizeAnswer · 정답 공개 제한 · selfJudge · 소속검증 · 반복제출
3. 장애 내성         타임아웃 · 트랜잭션 · 429 · lifespan
4. 적재 정합성       DataLoader 가드 · 사유별 실패 · 챕터 순서
5. 그다음에야 화면   맞춤 문제 · 개념 설명
```

리뷰 원문: [`docs/review/gstack/`](docs/review/gstack/) ·
[`docs/review/oh-my-claudecode/`](docs/review/oh-my-claudecode/)

---

## 1. 채점을 서버로 되돌리기 🔴 지금 할 일

정본: [`docs/rules/grading-and-difficulty.md`](docs/rules/grading-and-difficulty.md)

- [ ] **정답 정규화가 서로 다른 답을 같게 만든다** 🔴 — `normalizeAnswer` 가 모르는 LaTeX
      명령어를 **지워서** `$\sqrt{2}$` 와 `2` 가 같아지고 `$\pi$` 는 빈 문자열이 된다
      (= `"$"` 한 글자로 정답 처리). 지우지 말고 「비교 불가」를 반환한다.
      🎯 **진단 테스트 객관식 채점에 지금 쓰이고 있다.** → 재현·대응:
      [`docs/review/oh-my-claudecode/REVIEW_2026-08-27_full-codebase.md`](docs/review/oh-my-claudecode/REVIEW_2026-08-27_full-codebase.md) §1
- [ ] **정답 공개 API 를 `SHORT_ANSWER` 로 제한** — `GET /api/problems/{id}/answer` 가
      `problemType` 을 안 봐서 **객관식 정답도 나간다.** 객관식은 서버가 채점하므로 줄 이유가 없다.
      🔴 **「이미 제출했는지」 검사는 넣지 말 것** — 단답형 자가채점은 *공개 → 판단 → 제출*
      순서라(`Assessment.tsx:120→136`) 그 검사를 넣으면 흐름이 깨진다.
      → `ProblemServiceImpl.revealAnswer`
- [ ] **반복 제출을 통계에 반영하지 않기** — 제출 응답이 정답을 알려주는데(설계) 중복 제출을
      안 막아서, **객관식만으로 정답률을 원하는 값으로 만들 수 있다.** 이력은 계속 쌓고
      통계에만 첫 제출을 반영한다. 🎯 `existsByUserIdAndProblemId` 조회는 아래 소속 검증과
      **공유**한다 (`UserProblemHistoryRepository` 는 지금 조회 메서드가 0개다).
- [ ] **제출 시 문제-챕터 소속 검증** — `problemId` 가 `chapterId` 에 속하는지 확인하지
      않아, 안 푼 챕터에 정답을 쌓을 수 있다. → `ProblemServiceImpl.submitAnswer`
- [ ] **`selfJudge` 누락을 400 으로** — 지금은 `Boolean.TRUE.equals(...)` 라 **빠지면 오답**
      으로 기록된다. 프론트 버그가 학생 정답률을 깎는다. 🔴 「모른다」를 값으로 접지 않는다.
- [ ] **자가채점 자체를 재검토** — 단답형 채점을 클라이언트에 맡길지, 서버가
      `normalizeAnswer` 로 채점하고 이의제기를 따로 받을지. ⚠️ **사용자가 정할 일**이다
      (아래 6절).
- [ ] **동시 제출 경쟁 처리** — `USER_CHAPTER_STATS (user_id, chapter_id)` 는 unique 인데
      getOrCreate 가 잠금 없는 read-then-write 다. 같은 유저의 동시 요청에서 제약 위반.

## 2. 인증 경계 🔴 지금 할 일

- [ ] **챗봇 세션을 유저에 묶기 — 🔴 Spring 부터 고쳐야 한다**
      🔄 **2026-08-27 정정.** 원래 여기 *「키를 `ailang:chat:{user_id}:{session_id}` 로」*
      라고 적혀 있었다. **그대로 하면 안 고쳐진다.**
      `AiChatController` 에 `@AuthenticationPrincipal` 이 없어 **학생 신원이 FastAPI 로 아예
      안 간다.** 토큰 subject 가 `service@internal` 고정이라 FastAPI 의 `user_id` 는
      **모든 학생에게 같은 값**이고, 키를 바꿔도 전부
      `ailang:chat:service@internal:{session_id}` 가 되어 하이재킹이 그대로 남는다.
      → ① `AiChatController` 가 학생을 받아 ② `ChatRequest` 에 식별자를 실어 보내고
      ③ 그 다음에 FastAPI 키를 바꾼다. 근거:
      [`docs/review/oh-my-claudecode/REVIEW_2026-08-27_full-codebase.md`](docs/review/oh-my-claudecode/REVIEW_2026-08-27_full-codebase.md) §1 🔴-4
- [ ] **토큰에 종류 클레임(`typ`)을 넣는다** 🔴 — 액세스와 리프레시가 **만료 시간만 다르고
      나머지가 같다.** 필터는 서명·만료만 보므로 **리프레시 토큰을 `accessToken` 쿠키에 넣으면
      모든 API 가 통과**한다. 로그아웃은 액세스만 블랙리스트에 넣으므로, 유출된 리프레시
      토큰은 **로그아웃 후에도 7일간 유효**하다.
      🎯 이 하나로 「서비스 토큰과 학생 토큰이 구분 안 된다」(아래)도 같이 닫힌다.
- [ ] **Redis 에 비밀번호를 걸고 바인딩 주소를 좁힌다** 🔴 — 그 안에 리프레시 토큰 **원문**,
      로그아웃 블랙리스트, `email:verified:*`(넣으면 이메일 인증을 건너뛴다)가 들어 있는데
      `6380:6379` 로 호스트 전체에 열려 있고 인증이 없다. → 키 목록:
      [`docs/DATA_CONTRACT.md`](docs/DATA_CONTRACT.md) §5.5
- [ ] **FastAPI 도 로그아웃을 알게 하기** — Spring 만 Redis 블랙리스트를 본다. `8001` 이
      호스트에 열려 있어 로그아웃한 토큰으로 `/ai/chat` 직접 호출이 된다.
- [ ] **내부 서비스 토큰을 유저 토큰과 구분** — `AiServerClient` 가 `service@internal` 을
      subject 로 학생 토큰과 같은 형태의 토큰을 만든다. 별도 claim 을 넣고 AI 서버가 요구한다.
- [ ] **`8001` 포트를 호스트에 열어 둘 필요가 있나 확인** — 스프링만 부른다면 compose
      네트워크 안에만 두면 위 두 문제의 표면이 줄어든다.
- [ ] **`User.grade` null 처리** — 구글 가입은 학년을 안 받는데
      `getRandomProblemByGrade`·`getAssessmentProblems`·`getConcept` 가 `.name()` 을 부른다.
      🔴 NPE → 500. 학년 미입력 상태를 어떻게 보여줄지와 함께 정한다.

## 3. AI 호출 안정성 🟠

정본: [`docs/rules/ai-call-policy.md`](docs/rules/ai-call-policy.md)

- [ ] **`RestTemplate` 타임아웃** — 연결·읽기 둘 다 없다. AI 서버가 응답을 안 주면 톰캣
      스레드가 무한 대기한다. → `AppConfig.restTemplate`
- [ ] **`getAiProblem` 트랜잭션 축소** — `@Transactional` 안에서 수 초짜리 HTTP 호출을 한다.
      그동안 Oracle 커넥션을 잡고 있다. 저장 시점에만 연다.
- [ ] **429 를 429 로 전달** — FastAPI 는 할당량 초과를 429 로 내리는데
      `GlobalExceptionHandler` 의 `RuntimeException` 분기가 500 으로 덮는다.
      🔴 「한도 초과」·「모델 거부」·「JSON 깨짐」·「서버 죽음」이 지금 한 메시지다.
- [ ] **생성 결과를 저장 전 검증** — `PROBLEMS.OPTIONS` 는 `VARCHAR2(1000)` 인데 LaTeX 섞인
      보기 4개가 넘길 수 있다. 넘치면 `DataException` → 500.
- [ ] **JSON 파싱 실패 처리** — ` ```json ` 펜스가 없거나 JSON 이 아니면
      `json.loads` 가 던지고 끝난다. 재시도 또는 명확한 실패 응답. → `problem_service.py`

## 4. 설정·환경 🟡

- [ ] **적재 경로 하드코딩 제거** — `application.yml` 에
      `C:/Users/USER/OneDrive/Desktop/...` 가 박혀 있다. 🔴 다른 PC 는 문제 0건으로 뜨고
      경고 로그만 남는다. → 환경변수 (`.env.example` 참고)
- [ ] **CORS·리다이렉트 주소 환경변수화** — `http://localhost:5173` 이 `SecurityConfig` 와
      `application.yml` 두 곳에 문자열로 있다.
- [ ] **`requirements.txt` 누락 보완** — `pydantic-settings`(config.py 가 import),
      `google-api-core`(`ResourceExhausted` import)가 없다. 지금은 langchain 을 통해
      전이 설치돼 우연히 돈다.
- [ ] **Dockerfile 의 `--reload` 제거** — 개발 옵션이 이미지에 들어가 있다.
- [ ] **`spring.jpa.show-sql` 과 로그 레벨** — 운영에서 SQL 전문이 찍힌다.
      🔄 **2026-08-27 정정 · 항목 삭제.** 여기 「커밋 인코딩 고치기」가 있었는데 **틀린
      진단이었다.** 바이트를 읽어 보니 이 레포의 커밋 메시지는 **전부 유효한 UTF-8** 이고,
      `01802c1` 이 이상해 보인 것은 한글 사이에 라틴 문자 넷(`ã` `ê` `ë`)과 `c` 가 섞여
      들어갔기 때문이다 — 긴 `-m` 인자를 셸에서 타이핑하다 생긴 **입력 사고**다.
      고칠 설정이 없다. 긴 메시지는 에디터로 쓴다.
- [ ] **springdoc 도입 검토** — [`docs/API_CONTRACT.md`](docs/API_CONTRACT.md) 를 손으로
      관리하고 있다. ⚠️ 손 문서는 낡는다 (이미 한 번 낡았다). 자동 생성으로 바꿀지 정한다.
- [ ] **`DataLoader` 가드를 `SOURCE_TYPE='REAL'` 기준으로** 🔴 — 지금 가드가
      `problemRepository.count() > 0` 인데 `getAiProblem` 이 **같은 표에** AI 문제를 저장한다.
      적재 경로가 틀려 0건으로 뜬 상태에서 학생이 AI 문제를 **한 번만** 만들면 count=1 이 되어
      **다음 부팅부터 기출 적재를 영원히 건너뛴다.** 로그는 「이미 존재합니다」라고 안심시킨다.
- [ ] **적재 실패를 사유별로 구분** — `Map.of` 는 **null 키 조회 자체가 NPE** 라, JSON 에
      `question_grade`·`question_step` 이 없으면 바로 아래 「알 수 없는 학년 코드」 경고에
      **도달하지 못하고** 바깥 catch 로 떨어져 `"처리 실패: null"` 한 줄만 남는다.
      그리고 `int questionDifficulty` 는 필드가 없으면 0 → **조용히 `LOW` 로 적재**된다.
- [ ] **챕터 `orderNum` 이 실행 환경마다 달라진다** — 「먼저 만난 파일」의 `question_unit` 으로
      정해지는데 `listFiles()` 순서가 OS 의존이다. `CHAPTERS(grade,title)` 유니크 제약도 없다.

## 5. 검사 — 🔴 지금 근거가 없다

현재 이 레포의 테스트는 **컨텍스트 로딩 1개**가 전부다.

- [ ] **`normalizeAnswer` 픽스처** — LaTeX (`$\frac{3}{4}$`), 원문자 (`①`), 복수정답
      (`①, ③`), `\times`·`^{n}`. 🔴 지금 정규화가 맞는지 아무도 모른다.
- [ ] **난이도 전이 경계** — `LOW` 에서 더 내릴 때, `HIGH` 에서 더 올릴 때,
      `totalCount` 가 2→3 이 되는 순간. → `Difficulty`, `UserChapterStats`
- [ ] **`DataLoader` 두 번 실행** — 멱등성은 한 번 돌리는 검사로는 안 보인다.
- [ ] **AI 클라이언트는 스텁으로** — 🔴 실제 Gemini 를 부르는 테스트를 만들지 않는다.
- [ ] **새 검사는 옛 코드에서 빨간불이어야 한다** — 양쪽 다 통과하면 아무것도 못 박은 게 없다.

## 6. Phase 3 — RAG ⏸ 1·2절 뒤

🔴 **지금 「구현돼 있다」고 읽힐 만한 흔적이 세 개 있는데 코드는 0줄이다:**
`requirements.txt` 의 `supabase`, `config.py` 가 **필수로 요구하는** Supabase 키 두 개,
그리고 `rag_service.py` 의 `# TODO` 한 줄.

- [ ] **AI Hub 수학 자료 전처리** — 텍스트 추출·정제·청킹(약 500 토큰)
- [ ] **Supabase pgvector 테이블 생성** — 스키마는 [`docs/DATA_CONTRACT.md`](docs/DATA_CONTRACT.md) §6 에 적는다
- [ ] **메타데이터 태깅** — `grade`, `chapter_title`
- [ ] **임베딩 적재 파이프라인** — 재실행해도 중복이 안 생기게
- [ ] **`rag_service.py` 검색 구현** — 학년 필터 + 코사인 유사도 → 프롬프트 주입
- [ ] **검색 결과가 없을 때의 동작 결정** — ⚠️ 빈 컨텍스트로 그냥 답하게 둘지, 「자료 없음」을
      밝힐지. 근거 없는 답을 근거 있는 답처럼 보이게 하면 안 된다.

## 7. Phase 4 — 관리자 · 마무리 ⏸

- [ ] 관리자 챕터 CRUD (`/api/admin/chapters`)
- [ ] 관리자 문제 CRUD (`/api/admin/problems`)
- [ ] `ROLE_ADMIN` 인가 경로 — 🔴 현재 `SecurityConfig` 에 역할 기반 규칙이 하나도 없다
      (`anyRequest().authenticated()` 뿐)
- [ ] AI 생성 문제 검수 화면 — `SOURCE_TYPE = 'AI'` 인 문제의 정답을 사람이 확인하는 자리
- [ ] 전체 통합 테스트
- [ ] 배포 대상 결정 — ⚠️ 아래 8절

---

## 8. 결정 대기 — 🔴 혼자 정하지 말 것

| 항목 | 왜 지금 못 정하나 |
| --- | --- |
| **세션 설계 한 덩어리** 🔴 | ① 토큰 종류(`typ`) ② 한 계정 몇 기기 ③ 서비스 호출 신원 — **셋이 같은 결정**이다. 지금은 리프레시 토큰이 이메일 하나로만 저장돼 **두 번째 기기 로그인이 첫 기기를 튕겨낸다.** 의도인지부터 정한다 |
| **단답형 채점 방식** | 자가채점을 유지할지, 서버 채점 + 이의제기로 바꿀지. 학습 경험의 문제라 사용자가 정한다. ⚠️ 진단 테스트 흐름이 여기 의존한다(`Assessment.tsx`) |
| **인증코드 잠금 횟수** | 「몇 번 틀리면 잠그나」·「한 주소에 몇 번 보낼 수 있나」 |
| **진단 테스트 결과를 초기 난이도에 쓸 것인가** | 지금은 제출이 챕터 통계에 흩어져 들어가 `totalCount < 3` 에 머문다 — 난이도는 안 움직이고 **정답률 분모만 오염된다.** 초기값으로 쓸지, 통계에서 뺄지 |
| **스키마 마이그레이션 도구** | `ddl-auto: update` 를 언제까지 쓸지. 운영 데이터가 생기기 전에 정해야 한다 |
| **배포 대상** | compose 에 스프링이 없다. 어디에 띄울지가 정해져야 포트·오리진·시크릿 관리가 정해진다 |
| **AI 생성 문제의 검수 절차** | 사람이 볼지, 규칙으로 거를지, 학생에게 「AI 문제」라고 표시할지 |
| **RAG 자료 출처와 라이선스** | AI Hub 자료를 벡터 저장소에 넣어도 되는 범위 |

---

## ✅ 끝난 것

| 무엇 | 결과 | 정본 |
| --- | --- | --- |
| 인증 기반 (Phase 1) | 이메일 인증·JWT·구글 OAuth2·LOCAL↔GOOGLE 중복 차단·로그아웃 블랙리스트 | [docs/API_CONTRACT.md](docs/API_CONTRACT.md) §1 |
| 문제 풀이 핵심 (Phase 2) | 챕터·문제·이력·통계 4개 표, 맞춤/랜덤/학년별/진단 문제, 제출·난이도 재계산 | [docs/DATA_CONTRACT.md](docs/DATA_CONTRACT.md) §2 |
| AI 연동 | 개념 설명·모의문제·챗봇 3개 엔드포인트, Gemini 할당량 예외 처리(FastAPI 쪽) | [docs/rules/ai-call-policy.md](docs/rules/ai-call-policy.md) |
| 기출문제 적재 | AI Hub JSON → `PROBLEMS`, 챕터 자동 생성, 원문자 정답 정규화 스크립트 | [docs/research/aihub-json-structure.md](docs/research/aihub-json-structure.md) |
| 문서·규율 체계 (2026-08-27) | `commerce-signal` 관례 이식, `PROJECT_PLANNER.md` 를 4개 정본으로 분리 | [docs/00_CODE_WALKTHROUGH.md](docs/00_CODE_WALKTHROUGH.md) |
