# docs/rules — 꼭 지켜야 할 규율 모음집

**성격:** 프로젝트에서 반드시 지켜야 하는 정책·가드레일·의사결정 기록.

- [`../../CLAUDE.md`](../../CLAUDE.md)(세션 자동 로드)는 규율의 **요약·색인**이다.
  여기 `rules/` 에는 CLAUDE.md 에 다 담기엔 긴 **상세 정책·판정 기준·의사결정 로그**를 둔다.
  CLAUDE.md 에서 링크한다.
- 여기 들어갈 것 (예):
  - 채점·난이도·통계의 판정 기준과 경계 사례
  - 외부 모델 호출 정책 (타임아웃·실패 종류·프롬프트에 넣지 않을 것)
  - 인증 경계 정책 (두 서버가 무엇을 각자 알고 무엇을 공유하나)
  - AI 생성 문제의 검수 기준
  - 중요한 방향 전환의 의사결정 로그 (무엇을·왜·언제 정했나)
- 여기 두지 말 것: 진행 상황 보고(→ `../reports/`), 조사 원자료(→ `../research/`),
  표·컬럼·환경변수(→ `../DATA_CONTRACT.md`), 엔드포인트(→ `../API_CONTRACT.md`).

파일명: `<주제>.md` (예: `grading-and-difficulty.md`, `ai-call-policy.md`).

## 현재 문서

| 문서 | 내용 |
| --- | --- |
| **[grading-and-difficulty.md](grading-and-difficulty.md)** | 🔴 **채점·난이도·통계 건드리기 전 필독** — 누가 정답을 정하는가 |
| **[ai-call-policy.md](ai-call-policy.md)** | 🔴 **AI 호출 코드 쓰기 전 필독** — 어디서·어떻게 부르고, 실패를 어떻게 남기나 |
| [stack-decision.md](stack-decision.md) | 왜 서버가 둘인가, 무엇을 아직 안 정했나 |
