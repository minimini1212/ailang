# docs/review/oh-my-claudecode — oh-my-claudecode 리뷰 결과

**성격:** oh-my-claudecode 경로(`code-reviewer` · `critic` · `security-reviewer` ·
`architect` · `verifier` 에이전트, `/code-review` 등)로 수행한 설계·코드 리뷰 산출물.

- 파일명: `REVIEW_<YYYY-MM-DD>_<주제>.md`.
- 리뷰 문서 상단에 **대상(문서·커밋 범위)·리뷰 시점 국면·심각도 범례**를 기록해, 나중에
  읽어도 「무엇을 기준으로 지적했는지」를 알 수 있게 한다.
- **지적의 수용 여부는 이 폴더에서 정하지 않는다.** 수용된 항목만
  [`../../DATA_CONTRACT.md`](../../DATA_CONTRACT.md) ·
  [`../../API_CONTRACT.md`](../../API_CONTRACT.md) · [`../../rules/`](../../rules/) ·
  [`../../../TODOS.md`](../../../TODOS.md) 로 승격한다
  (상위 [`../README.md`](../README.md) 규율).

## ⚠️ gstack 쪽과 다른 점

| | gstack | 여기 |
| --- | --- | --- |
| 진행 | 대화형 — 지적마다 즉석 확정 | 일괄 — 지적 목록이 먼저 나온다 |
| 결정 | 리뷰 문서에 같이 남는다 | 🔴 **별도로 남겨야 한다** |

🔴 **그래서 이 폴더가 상위 README 의 「결정 기록」 규율이 실제로 필요한 자리다.**
지적 목록만 저장하고 처리를 안 적으면, 다음 리뷰가 **이미 고친 것을 다시 지적**한다.
건별로 `처리 · 확인한 자리(파일:줄) · 근거` 를 문서 끝에 이어 붙인다.

## 🎯 이 프로젝트에서 특히 값이 나오는 자리

| 에이전트 | 왜 여기서 쓸 만한가 |
| --- | --- |
| `security-reviewer` | 인증 경계가 두 서버로 갈려 있고, 남은 위험이 [`../../../TODOS.md`](../../../TODOS.md) 2절에 모여 있다 |
| `test-engineer` | 🔴 **테스트가 1개뿐이다.** 채점·난이도는 순수 로직이라 붙일 수 있다 ([`../../rules/grading-and-difficulty.md`](../../rules/grading-and-difficulty.md) R8) |
| `critic` | 결정 대기 항목의 선택지를 되짚을 때 ([`../../PRD.md`](../../PRD.md) §7) |
| `verifier` | 「고쳤다」의 근거를 확인할 때 |

## 🔴 자동 리뷰의 지적을 그대로 옮기지 않는다

- **틀린 지적이 섞여 온다.** 코드를 읽고 대조한 뒤에 문서에 올린다.
  근거 등급(확인함 / 코드 대조 / ⚠️ 추정) 표기 규칙: [`../README.md`](../README.md)
- ⚠️ **자바 쪽에서 자주 나오는 헛지적**: Lombok 이 만드는 메서드를 못 보고
  「getter 가 없다」고 하거나, `@Builder.Default` 를 못 읽고 「기본값이 안 먹는다」고 한다.
- 🔴 **이미 정해진 것**(자가채점 · snake_case 계약 · `supabase` 미사용 · `ddl-auto`)은
  반려하고 근거를 링크한다: [`../README.md`](../README.md) 「이 프로젝트에서 특히 주의할 것」

## 현재 문서

| 파일 | 대상 | 결과 |
| --- | --- | --- |
| [REVIEW_2026-08-27_full-codebase.md](REVIEW_2026-08-27_full-codebase.md) | 전수 코드 (커밋 `01802c1`, 작업 트리 상태) | **40건** (🔴 5 · 🟠 18 · 🟡 17) · REQUEST CHANGES |
