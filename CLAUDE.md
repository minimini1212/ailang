# CLAUDE.md — ailang (AI Math Learning Platform)

**Rules only.** Status, measurements, and decision history live in the source-of-truth docs
listed below — never duplicate them here. `PROJECT_PLANNER.md` went stale exactly this way: it
kept saying "Spring Boot 3.x" and "Gemini 1.5 Flash" months after the code moved to Spring Boot
4.0.2 and `gemini-2.5-flash`. A document that restates what already has an owner elsewhere will
drift, and **stale text is not harmless — the next implementer builds the retired design from it.**

> Two servers, one product: a Spring Boot app (auth, problems, statistics) and a FastAPI app
> (Gemini calls). They are deployed separately and share only a JWT secret and an HTTP contract.
> **Neither one imports the other's code, and neither one reaches the other's datastore.**

## Language

- **Reply to the user in Korean.**
- **All repo artifacts stay Korean**: docs, reports, commit messages, code comments, test names,
  API error messages. Their audience is the team, the mentor, and eventually students.
- **This file is English** because it is instructions for Claude, not a deliverable.
- **Commit messages: write them in an editor, not as a long `-m` string.**
  🔄 *Corrected 2026-08-27.* This rule previously said the history had mojibake and told you
  to check `i18n.commitEncoding`. **That was wrong** — every commit message in this repo is
  valid UTF-8. What `01802c1` actually contains is four stray Latin letters
  (`ã` `ê` `ë`, U+00E3/EA/EB) dropped into Korean text, plus a stray `c` — the signature of
  **keystrokes landing in a shell while typing a long multi-line `-m` argument**, not of an
  encoding problem. Nothing needs configuring; just don't type paragraphs at a `-m` prompt.
  📌 The general lesson is the one this file already teaches elsewhere: *the symptom that
  looks like an encoding bug is usually an input bug.* Read the bytes before naming a cause.

## Sources of truth — read these, don't restate them here

| Question | File |
| --- | --- |
| **Who decides whether an answer is correct?** | `docs/rules/grading-and-difficulty.md` **(read before touching 채점·난이도·통계)** |
| **May I call Gemini from here?** | `docs/rules/ai-call-policy.md` **(read before writing any AI call)** |
| What are we building, and why? | `docs/PRD.md` |
| Table names, columns, enums, env vars | `docs/DATA_CONTRACT.md` **(read before writing code)** |
| What does each endpoint take and return? | `docs/API_CONTRACT.md` |
| How does the code fit together? Why does this file exist? | `docs/00_CODE_WALKTHROUGH.md` |
| Why two servers? Why this stack? | `docs/rules/stack-decision.md` |
| What shape is the AI Hub source data? | `docs/research/aihub-json-structure.md` |
| Current state, deferred work, pending decisions | `TODOS.md` (top section) |
| What happened on a given day | `docs/reports/daily/REPORT_<date>.md` |

If a rule below conflicts with a source-of-truth doc, **the doc wins** — then fix this file.

---

## HARD RULES

### 🔴 The server grades. The client never does.

This is the defect the product's whole value rests on.

> *"정답 판정과 통계 갱신은 서버 안에서만 일어난다. 클라이언트가 보낸 판정 결과를 저장하지 않는다."*

- **Never accept a correctness verdict from the request body.** `SubmitAnswerRequest.selfJudge`
  is exactly this — the client tells the server whether the student got it right, and the server
  writes it to `USER_PROBLEM_HISTORY` and to the difficulty algorithm. Anyone with the network
  tab can make every answer correct. It is documented as 자가채점 for 단답형; that is a **product
  decision that has not been re-approved**, and until it is, no new endpoint may copy the pattern.
- **The answer never leaves the server before submission.**
  🔄 *Fixed 2026-09-02 — `GET /api/problems/{id}/answer` now serves `SHORT_ANSWER` only.*
  It used to ignore `problemType` and hand out **객관식 answers too**, before the student had
  submitted anything. The rule stands: any endpoint that returns
  `answer` or `explanation` must state, in code, which problem types and which lifecycle stage
  it is for.
- **Every write must verify the ids belong together.**
  🔄 *Fixed 2026-09-02.* `submitAnswer` takes `problemId` from the path and `chapterId` from
  the body; it used to skip the relation check, so a request could pile correct answers onto a
  chapter the student never opened.
  🎯 **Rule: whenever a request carries two ids, the server proves the relation before writing.**
- Grading, difficulty transitions, and statistics are **pure functions or entity methods** —
  never inline in a controller — so they can be tested without a DB.

### 🔴 Never fold "unknown" into a value

- **「안 풀었다」 ≠ 「틀렸다」.** **A missing verdict is a rejected request (400), not a
  wrong answer.** 🔄 *Fixed 2026-09-02.* The code read
  `Boolean.TRUE.equals(request.getSelfJudge())`, which turns a missing field into a recorded
  오답 — a frontend bug would quietly drag a student's 정답률 down, and 정답률 is what picks
  their next problem.
- **`totalCount < 3` is not "이 학생은 못한다".** It is *"아직 모른다"*, which is why the
  algorithm holds difficulty there. Do not add a code path that treats it as a low score.
- **`grade == null` is not ELEM_3.** `User.grade` is nullable (Google OAuth signup does not
  collect it). Handle the unknown state; never substitute a default.
  🔄 *Fixed 2026-09-10.* `user.getGrade().name()` sat in five places and threw NPE → 500.
  It now goes through one function (`UserGrades`) that returns a 400 telling the student to set
  their grade, and 마이페이지 gives them somewhere to set it.
- 🔴 **When a field needs three states, do not use a boolean.** Applies to grading results, AI
  call outcomes, and data-load outcomes alike.

### 🔴 AI-generated problems are never 기출문제

- `Problem.sourceType` separates `REAL` (AI Hub 기출) from `AI` (Gemini 생성). **Never save a
  generated problem without it**, and never let a query that means "기출" forget the filter.
- 🎯 A student's 진단 테스트 and 맞춤 문제 must draw from `REAL` only — a generated problem has
  no verified 정답 and would poison the very statistic that picks their next problem.
- **A generated problem is unverified until a human says otherwise.** There is currently no
  review step; do not present generated problems as if there were one.
- Vocabulary (`Grade`, `Difficulty`, `ProblemType`, `SourceType`, `UserRole`, `AuthProvider`)
  lives in `domain/**/enums/` — **never hand-type these strings.** The code already carries
  `"MEDIUM"` as a literal in `ProblemServiceImpl` and passes `String` difficulty into native
  queries; that is the road to `"Medium"` silently matching nothing.

### External model calls (Gemini) — details in `docs/rules/ai-call-policy.md`

- 🔴 **Never inside a transaction.** Open the transaction at the save, not around the call.
  🔄 *Fixed 2026-09-01.* `getAiProblem` was `@Transactional` around a 13-second HTTP call,
  holding an Oracle connection the whole time. The DB work moved to `AiProblemStore` and the
  method is now `NOT_SUPPORTED` — ⚠️ **declaring that explicitly matters**: a class-level
  `@Transactional(readOnly = true)` would otherwise keep a transaction open across the call.
- 🔴 **Every outbound call has a connect timeout and a read timeout.**
  🔄 *Fixed 2026-09-01 — connect 5s, read 60s, both config keys.* The shared `RestTemplate`
  had neither, so an AI server that accepts the connection and never answers pinned a Tomcat
  thread forever. ⚠️ The read timeout is long because measured AI responses are 13–19 seconds.
- 🔴 **Preserve the failure kind across the hop.** FastAPI correctly returns `429` for a Gemini
  **Quota exhausted, model refused, malformed JSON, and server down are four different states.**
  🔄 *Fixed 2026-09-01.* FastAPI returned `429` correctly, then Spring's `RestTemplate` threw
  and `GlobalExceptionHandler`'s `RuntimeException` branch rewrote it to `500`, so the student
  saw one useless message. `AiServerClient` now preserves `429`/`502`/`503`.
- 🔴 **Never send personal data to the model.** The prompt carries 학년 · 단원명+유형 · 문제 본문
  and nothing else (🔄 유형 joined 2026-09-09 — 대단원 8개 alone was too coarse for concept
  explanations). Keep it that way: no email, no nickname, no user id, no answer history.
  ⚠️ Re-check this list whenever a request DTO to the AI server gains a field.
- **A generated payload is untrusted input.** Validate it against the column limits before
  saving — `PROBLEMS.OPTIONS` is `VARCHAR2(1000)` and four LaTeX-bearing 보기 can exceed it.
- **Prompts live in one place per service**, not scattered across call sites.

### Data safety

- **Secrets are never hardcoded** — `.env` / env vars only.
  **Claude never writes `.env`.** Ask the user; they add it themselves. The key list's truth is
  `.env.example`.
- 🔴 **No absolute machine paths in committed config.** **Paths, intervals, limits, and model
  names are config, not code.** 🔄 *Fixed `6615ba4`.* `application.yml` shipped
  `C:/Users/USER/OneDrive/Desktop/…` as the data-load directory, so every other machine loaded
  zero problems and only logged a warning. ⚠️ That warning-not-error behaviour is **still** how a
  wrong path fails — check the insert count in the boot log.
- 🔴 **`ddl-auto` is `update` today.** It must never be `create` or `create-drop`, and moving to
  a migration tool is a decision for the user, not a side effect of another task.
- **`DataLoader` runs at every boot.** Its only guard is `countBySourceType(REAL) > 0`. Do not
  add a loader that writes without an equally explicit guard, and never make one that deletes.
- **No student personal data leaves the system.** Email exists for auth only; it is never a
  prompt input, a log line in production, or a query parameter.

### Correctness

- **Writes are idempotent.** Re-running `DataLoader` over the same directory must not duplicate
  problems or chapters. The guard is all-or-nothing (`countBySourceType(REAL) > 0`), so a
  partially loaded set can never be completed. The real key is the AI Hub `id` — the column
  exists (`PROBLEMS.SOURCE_ID`, UNIQUE) but the guard does not use it.
  ⚠️ Moving to it is **a decision, not a cleanup**: per-record checking means re-reading ~2,300
  JSON files on every boot. It is in `TODOS.md` §8, and the idempotency test is blocked on it.
- **Persist failures with their kind.** `DataLoader` collapses "답안 파일 없음", "학년 코드 모름",
  "정답 추출 실패", and "파싱 예외" into one `skipped` counter, so *which* 문제 need fixing is
  unknowable. Success, skipped-for-reason-X, and failed are not one number.
- **Concurrent submissions must not violate the unique constraint.**
  🔄 *Fixed 2026-09-11, and **the fix did not work until 2026-09-15**.* The rule generalises:
  **whenever a write is "read, then modify", take the lock before the read that decides the
  write**, and do the *create* in its own transaction.
  🔴 **And catch the failure outside that transaction, not inside it.** Swallowing a constraint
  violation in the `REQUIRES_NEW` method itself looks right and is not: the transaction is
  already marked rollback-only, so returning normally makes the commit throw
  `UnexpectedRollbackException` at the caller. **The exception changes name; the 500 does not.**
  That stood for four days behind a comment that said the student no longer sees it.
  🔴 And never hold such a lock across an outbound call.
  Details: `docs/rules/grading-and-difficulty.md` R10 · **R10-1**.
- **Trust measurements over docs** for data shape and distributions. Query the real tables before
  writing a query that defines a segment or a denominator.
- **Errors must say which user / problem / chapter / URL failed and why.**

### Auth and session

- 🔴 **A per-user resource is keyed by the authenticated user, never by a client-supplied id.**
  🔄 *Fixed 2026-09-01 — the key is now `ailang:chat:{user_id}:{session_id}`.* It used to be
  `ailang:chat:{session_id}` with `session_id` straight from the request body, so any logged-in
  user could read another student's conversation by supplying their session id.
  ⚠️ **The first attempt at this fix did nothing**: the `user_id` put into the key was the
  token's subject, which was the same fixed value (`service@internal`) for every student.
  Keying by 「the authenticated user」 only helps if the value actually varies per user.
- 🔴 **The two servers must agree on what "logged out" means.** Spring checks the Redis
  blacklist; FastAPI's `verify_token` checks signature, expiry and `typ` — **not the blacklist**.
  ⚠️ Narrowed, not closed: a student's token is now rejected outright (wrong `typ`), and since
  2026-09-15 port `8001` is bound to `127.0.0.1` rather than every interface. What remains is
  Spring calling the AI server on behalf of a logged-out student — and Spring's own filter stops
  that first.
- **Internal service calls are not user tokens.** A service identity needs its own claim, and the
  receiving server must require it. 🔄 *Fixed 2026-09-01 — tokens carry `typ`
  (`ACCESS`/`REFRESH`/`SERVICE`) and FastAPI accepts `SERVICE` only.* Before that,
  `AiServerClient` minted a token for the fabricated subject `service@internal` with the same key
  and the same claims as a student's, so the two were indistinguishable.
- **CORS and the OAuth redirect target are config, not literals.**
  🔄 *Fixed 2026-09-11.* Both now derive from a single key, `app.frontend.origin`
  (`APP_FRONTEND_ORIGIN`); the backend's own callback base is a **separate** key,
  `APP_OAUTH2_CALLBACK_BASE`, because it is a different address with a different owner.
  The rule stands for the next address: **one key, every reader** — the failure it prevents is
  "login succeeds but the screen never receives the response", whose symptom points at login
  and not at CORS. Key list: `.env.example` ⑧ · `docs/DATA_CONTRACT.md` §6.

---

## Tech stack

Java 21 · Spring Boot 4.0.2 · Gradle · Oracle 23ai (Docker) · Redis
Python 3.11 · FastAPI · LangChain · Gemini `gemini-2.5-flash`

Rationale and what is deliberately still open: `docs/rules/stack-decision.md`.

**Not decided — do not pick one unilaterally**: the RAG vector store integration (the Supabase
dependency is installed and zero lines of code use it), a schema migration tool, and any
production deployment target. Per `docs/PRD.md`: no new infrastructure until a real bottleneck is
measured.

---

## Working rules

- **One step at a time.** Don't implement several phases because they seem related.
- 🔴 **Fetch first, and read the branches that are not merged yet.**
  *Added 2026-09-15, after half an hour of work was thrown away.* A session started from a local
  `main` four days stale. Three pull requests had landed in the meantime, one of them
  (`12955e8`) doing **exactly** the config work the session then did again — and doing it better.
  Seven files conflicted at merge time and every duplicated line was discarded.
  ```
  git fetch
  git log --oneline origin/main -5
  git branch -r --no-merged origin/main
  ```
  🔴 **Then open the diff of any unmerged branch whose name touches your task.** That session
  *saw* `chore/config-externalize` in the branch list and skipped it. **A branch name is a
  label, not a summary** — the work inside it is either already done or about to collide
  with yours, and both are things you need to know before you start.
- **One branch per piece of work — not per change.** Cut a task branch from the current work
  branch; never commit directly to `main`.
  🔄 *Sized 2026-09-11 by the user: branches were being cut far too fine — one of them carried
  a twelve-line edit to this file and nothing else.*
  The unit is **what one person would review and verify in a single sitting** — usually one
  `TODOS.md` section, or one coherent theme. Not one bullet, one file, or one rule tweak.
  - A **doc or rule change rides along** with the work that motivated it. It never gets its own
    branch. *(This very rule was committed onto the branch that already owned the push rules.)*
  - A **fix and the tests that pin it are one branch.** If two changes are checked by the same
    test run, they ship together.
  - **Split only when the parts would be reviewed, reverted, or merged separately** — config
    with no behaviour change is not the same review as a change inside the grading path.
  - ⚠️ Too coarse has its own cost: if you cannot say in one line what the branch is for, or a
    reviewer would have to hold two unrelated failures in their head, it is two branches.
- 🔄 **Claude pushes the task branch when the task is finished.** *Changed 2026-09-11 — this
  line previously read "The user pushes … and never pushes."* What did **not** change:
  Claude commits only when asked, **never pushes to `main`**, never force-pushes, and never
  opens or merges a pull request unless asked. A push publishes the work; if the branch is not
  actually finished, say so and don't push.
- **Say what was verified and what was not, in the commit message.**
  🔴 **An unverified change must be labelled unverified** — a green-looking commit that nobody
  built is the same failure mode as a stale document.
  🔄 *Corrected 2026-09-15.* This rule used to assert, as a standing fact, that **"this machine
  has neither a JDK nor a running Docker."* A session repeated that line without checking and
  told the user to install a JDK **that was already there** — Gradle's toolchain keeps one under
  `~/.gradle/jdks/`, off `PATH`; Docker was running with three containers up.
  🔴 **Never state what this machine has or lacks from this file. Run the check.**
  ```
  ls ~/.gradle/jdks/            # toolchain JDKs Gradle already downloaded
  docker ps                     # what is actually up
  ```
- 🔴 **"Fixed but unverified" is a claim, not a fact. Verify it before building on it.**
  *Added 2026-09-15.* A commit dated 2026-09-11 said the concurrent-submission 500 was fixed and
  the code carried a comment promising the student no longer sees it. **Both were false** — and
  that same commit had honestly written *"the next person should read this as «claims to have
  fixed it», not «it is fixed»."* Nobody did, for four days, until a test ran it for real.
  When a doc, a comment, a commit or `TODOS.md` says *fixed, couldn't check*, that is **the
  first thing to check**, not the thing to build on. Closing it is worth more than new work.
- **Business logic is pure functions or entity methods** (grading, difficulty transitions,
  answer normalisation, LaTeX handling) so it can be tested without a DB or a network.
- **Fix the source, not the symptom.** If a rule above names a defect, fixing that one call site
  is not the fix — the rule describes a class.
- Prefer ASCII diagrams in comments for non-obvious flows and state transitions. If you change
  code near one, update it in the same commit — a stale diagram actively misleads.

### Tests

🔄 *This paragraph used to say "there is currently one test in this repo" and to treat every
number as unverified. That stopped being true on 2026-09-11.* **How many tests there are, and
what they cover, lives in `TODOS.md` §5 — read it there, and don't copy the number here.**

🔴 **Coverage is pure logic, plus two things that need a real DB.**
🔄 *2026-09-15: submission under concurrency and the `SOURCE_ID` unique constraint are now
measured — against a real Oracle, and the concurrency test found a live defect the moment it
first ran. 2026-09-15 (later the same day): the FastAPI side went from **0 tests to 78**, and the
AI hop is covered on both sides with the client stubbed.* **Loading is still unmeasured and the
frontend has no tests at all**; a green run says nothing about those.

- 🔴 **The AI hop is covered as a «contract», not as a model.** Both sides stub the client. What
  a real model actually returns is still unmeasured — deliberately, because a test that calls a
  live model is forbidden. Do not read these tests as "the AI works".

- **A test that needs a DB skips loudly when there is none** (`Assumptions`), and **never
  passes quietly**. Looking green without having run is worse than not running.
- **Such a test creates its own student and deletes it afterwards.** Touching an existing
  student's rows corrupts 정답률, and that corruption spreads into difficulty.

- **A new test must fail against the old code.** If it passes both ways it pins nothing.
  🎯 In practice: name, in a comment, *the mutation this test catches*. If you cannot name one,
  the test is decoration.
- ✅ **Grading and difficulty are covered** (2026-09-11): `AnswerNormalizer`,
  `UserChapterStats.recordAnswer` at 3-problem / 80% / 50% boundaries, and
  `Difficulty.upgrade`/`downgrade` at both ends. **They stay covered** — a change there that
  does not touch a test is a change that pinned nothing.
- **Batch behaviour is tested over two runs, not one** — `DataLoader` idempotency is invisible to
  a single-pass test.
- **Hard cases become fixtures**: LaTeX answers (`$\frac{3}{4}$`), 원문자 정답 (`①`), 복수정답
  (`①, ③`), 「상세페이지 참조」-style missing answer text, an AI response wrapped in a
  ```` ```json ```` fence, and an AI response that is not JSON at all.
- **Never test against a live Gemini call.** Stub the client; assert on the contract.

---

## Documentation rules

Goal: a person can answer "how does it work now / what changed and why / how far along are we"
without reading the code.

### Report writing (HARD)

`docs/research/` and `docs/reports/` are read by the mentor and by teammates who did not write
the code.

- **Never use internal jargon without explaining it.** Put a terminology section (§0) at the top
  as a table; reading only that section must be enough to follow the numbers.
- **Explain causes as 역할 → 작용 → 결과**, not by naming a module.
  *"클라이언트가 채점 결과를 보낸다 → 서버가 그대로 저장한다 → 정답률이 조작된다"*,
  not *"`selfJudge` 처리 문제"*.
- Module names and section numbers go in parentheses *after* the explanation, never in front.
- **No code identifiers in titles or summaries.** Developer trace info goes in an appendix.
- Ratios always carry their denominator. *"적재 실패 12%"* is meaningless; *"문제 1,340건 중
  161건 적재 실패"* is not.
- Test: *can a first-time reader interpret the numbers after reading only §0?* If not, rewrite.

### Where each document goes

| Kind | Location |
| --- | --- |
| Scope truth | `docs/PRD.md` |
| Table/column/enum/env truth | `docs/DATA_CONTRACT.md` |
| Endpoint request/response truth | `docs/API_CONTRACT.md` |
| Structure and flow overview | `docs/00_CODE_WALKTHROUGH.md` |
| Rules, detailed policy, decision records | `docs/rules/` |
| Daily work log | `docs/reports/daily/REPORT_<YYYY-MM-DD>.md` |
| Mentor-facing summary | `docs/reports/mentor/` |
| Handoff to another session | `docs/reports/HANDOFF_<date>_<topic>.md` |
| Research raw material (source data shape, external API findings) | `docs/research/` |
| Review output | `docs/review/<reviewer>/REVIEW_<date>_<topic>.md` |
| Deferred work — **what is left, nothing else** | `TODOS.md` |

**Every one of those folders exists and carries a `README.md` stating what belongs in it and
what does not. Read that README before adding a file there** — the split between them is the
whole point, and a report filed in the wrong place is the same as a report nobody wrote. The
`review/` README additionally carries the rule about not reading a findings table as a to-do
list, and a list of findings that are already-settled decisions here.

### Keep living documents current — in the same task

- Tables, columns, enums, env vars → `docs/DATA_CONTRACT.md` **and `.env.example`**
- New or changed endpoint, request field, response field, status code → `docs/API_CONTRACT.md`
- New file, changed file role, changed flow → `docs/00_CODE_WALKTHROUGH.md`
  (including the folder map and the **"last synced" date**)
- Deferred work → `TODOS.md`. **Reverse direction is mandatory**: when you implement an
  unchecked item, check it off in that same task.

**Trivial changes (typos, formatting, comments) are exempt.** Don't create doc noise.

### `TODOS.md` stays a to-do list, not an archive (HARD)

- **Only what is left goes here.** *What happened, what we measured, why it broke* belongs in
  `docs/reports/daily/`.
- **Completing an item replaces it — it does not annotate it.** Collapse it to one row in that
  section's `✅ 끝난 것` table: *what · result in one clause · link to the canonical doc.*
- **Retired items are deleted, not struck through.** `git` keeps them.
- **An open item is at most ~4 lines**: what, why it matters, what to touch.
- **Never restate a decision that has a home.** Link instead — copies are what go stale.
- **Size is a signal.** Past ~800 lines, narratives have leaked back in. Prune before appending.
- 🔴 **An unchecked box is not evidence.** Before acting on `[ ]`, verify against the code.
  `PROJECT_PLANNER.md` marked Phase 2 complete while `revealAnswer`, `selfJudge`, 진단 테스트,
  and AI 모의문제 — none of which it mentions — were already merged.

### Propagation check — grep the retired term (HARD)

Fixing only the source of truth leaves the rest quietly stale.

```
change a decision
  → list the retired terms, symbols, numbers
  → grep every living document (CLAUDE.md, README.md, TODOS.md, docs/**)
    and every place config repeats itself (application.yml, docker-compose.yml,
    .env.example, requirements.txt, build.gradle)
  → resolve each hit one of three ways
       ① fix it        (places that must state the current truth)
       ② footnote it   (places where the change itself matters — 🔄 mark + link to the truth)
       ③ leave it      (reports/ and review/ are point-in-time records — never edit after the fact)
```

⚠️ **This repo repeats config in five places.** A port, a URL, or a model name changes in
`application.yml`, `docker-compose.yml`, `.env.example`, `ai-server/app/config.py`, and the docs.
Redis is already `6380` on the host and `6379` inside the network. Grep before you believe you
changed it once.
🔄 *2026-09-11: the frontend origin used to be the standing example here — it was written twice
and is now one key. That is what resolving a hit looks like; the warning itself still holds.*

### Reviews leave their original (HARD)

After any design/code review, write `docs/review/<reviewer>/REVIEW_<date>_<topic>.md`
**in that session**. Minimum: target · scope · method · severity legend · **table of every
finding with its decided outcome** · rejected/deferred items with reasons · remaining risks.
Promote accepted conclusions to `DATA_CONTRACT.md` / `rules/` / `TODOS.md`; keep the original in
`review/` and never cite it as the source of truth.
