import json
import re
from langchain_core.messages import HumanMessage
from app.exceptions import LlmBadResponseException
from app.llm_call import ask

# 학년 코드를 한국어 학년명으로 변환
GRADE_DISPLAY = {
    "ELEM_3": "초등학교 3학년",
    "ELEM_4": "초등학교 4학년",
    "ELEM_5": "초등학교 5학년",
    "ELEM_6": "초등학교 6학년",
    "MIDDLE_1": "중학교 1학년",
    "MIDDLE_2": "중학교 2학년",
    "MIDDLE_3": "중학교 3학년",
    "HIGH_1": "고등학교 1학년",
}

DIFFICULTY_DISPLAY = {
    "LOW": "쉬운",
    "MEDIUM": "중간",
    "HIGH": "어려운",
}


class AiProblemService:
    """
    AI 모의문제 생성 서비스
    - 챕터명 + 난이도 + 학년을 프롬프트에 주입
    - 모델이 해당 조건에 맞는 수학 객관식 문제를 JSON 형식으로 생성
    """

    async def generate(self, chapter_title: str, difficulty: str, grade: str) -> dict:
        grade_name = GRADE_DISPLAY.get(grade, grade)
        # 🔴 어휘 밖의 값을 기본값으로 접지 않는다. 조용히 「중간」으로 바꿔치기하면
        #    프롬프트는 중간 문제를 만드는데 저장은 요청한 난이도로 되어 어긋난다
        #    (docs/rules/ai-call-policy.md).
        if difficulty not in DIFFICULTY_DISPLAY:
            raise LlmBadResponseException(f"알 수 없는 난이도: {difficulty}")
        difficulty_name = DIFFICULTY_DISPLAY[difficulty]

        prompt = f"""당신은 수학 문제 출제 전문가입니다.

학생 학년: {grade_name}
단원: {chapter_title}
난이도: {difficulty_name}

위 조건에 맞는 수학 객관식 문제를 1개 만들어주세요.
반드시 아래 JSON 형식으로만 응답하세요. JSON 외 다른 텍스트는 절대 포함하지 마세요.

[수식 작성 규칙] 모든 수식은 반드시 $...$ (인라인) 또는 $$...$$ (블록) 형식으로 감싸야 합니다.
예시: "분수 $\\frac{{3}}{{4}}$", "넓이 $S = \\pi r^2$", "방정식 $2x + 3 = 7$"

[JSON 이스케이프 규칙 — 매우 중요]
JSON 문자열 안에서 LaTeX 백슬래시는 반드시 두 번(\\\\) 써야 합니다.
한 번만 쓰면 \\times 가 탭 문자로, \\frac 이 줄바꿈 문자로 해석되어 수식이 깨집니다.
  올바름: "question": "$180 \\\\times x$ 의 값은?"
  틀림  : "question": "$180 \\times x$ 의 값은?"

{{
  "question": "문제 본문 (수식이나 조건을 명확하게 작성, 수식은 $...$로 감쌀 것)",
  "problem_type": "MULTIPLE_CHOICE",
  "options": ["보기1", "보기2", "보기3", "보기4"],
  "answer": "정답 번호 (1~4 중 하나의 숫자만)",
  "explanation": "풀이 과정과 정답 해설 (수식은 $...$로 감쌀 것, 학생이 이해하기 쉽게)"
}}"""

        content = (await ask([HumanMessage(content=prompt)])).strip()

        # ⚠️ 모델 응답은 믿을 수 없는 입력이다. 코드블록을 붙이거나 설명을 앞에 달 수 있다.
        #    예전 코드는 ``` 가 있으면 무조건 parts[1] 을 집었는데, 코드블록이 둘이면
        #    엉뚱한 것을 집고 없으면 전체를 파싱했다.
        data = _parse_json_object(content)
        _normalize(data)

        # options 리스트를 JSON 문자열로 변환 (Spring Boot DB에서 String으로 저장)
        if isinstance(data.get("options"), list):
            data["options"] = json.dumps(data["options"], ensure_ascii=False)

        # 🔴 저장 전에 계약을 확인한다. PROBLEMS.OPTIONS 는 VARCHAR2(1000) 이라
        #    LaTeX 섞인 보기 4개가 넘길 수 있고, 넘치면 DB 예외로 500 이 난다.
        _validate(data)
        return data


# ── 🔴 JSON 안의 LaTeX 백슬래시 문제 ─────────────────────────────────────────
#
# 2026-09-01 실호출에서 실제로 깨진 것을 확인했다:
#     모델이 낸 것   "$180 \times x$"
#     json.loads 뒤  "$180 <TAB>imes x$"     ← \t 가 «탭»으로 해석됐다
#
# JSON 에서 \t \n \r \f \b 는 제어문자 이스케이프다. 그래서 LaTeX 명령어 중
# 그 글자로 시작하는 것(\times \frac \neq \rightarrow \beta ...)은 **조용히 깨진다.**
# 반대로 \sqrt \pi \alpha 처럼 JSON 이 모르는 이스케이프는 파싱 자체가 실패한다.
#
# 🎯 프롬프트로 «\\ 로 써라» 라고 시켜도 모델이 늘 지키지는 않는다. 받아서 고친다.

# 🎯 제어문자는 명령어의 **첫 글자를 먹는다.**  "\times" → TAB + "imes"
#    그래서 맞춰야 할 것은 명령어 전체가 아니라 «첫 글자를 뺀 나머지»다.
#    (첫 판에서 전체 이름으로 맞췄다가 \n + "number" 를 \nu + "mber" 로 잘못 고쳤다.)
#
# ⚠️ 짧은 나머지는 일부러 뺐다. \ne → "e" 나 \nu → "u" 를 넣으면
#    「줄바꿈 + e/u 로 시작하는 낱말」이 전부 오검출된다. 못 고치는 편이 낫다.
_MANGLED = {
    "\t": ("t", ("imes", "heta", "riangle", "ext", "an")),
    "\n": ("n", ("abla", "eq", "ot")),
    "\r": ("r", ("ightarrow", "ight", "floor", "ceil", "ho")),
    "\f": ("f", ("rac", "orall", "loor")),
    "\b": ("b", ("igcirc", "igcup", "igcap", "ecause", "inom", "eta", "mod", "ar")),
}
# 긴 것부터 맞춰야 "ight" 가 "ightarrow" 를 가로채지 않는다.
_MANGLED_RE = {
    ch: (first, re.compile("(?:" + "|".join(sorted(rest, key=len, reverse=True)) + ")"))
    for ch, (first, rest) in _MANGLED.items()
}

# JSON 이 아는 이스케이프. 이 밖의 글자가 백슬래시 뒤에 오면 파싱이 깨진다.
_VALID_JSON_ESCAPE = set('"\\/bfnrtu')


def _repair_mangled_latex(s: str) -> str:
    """제어문자로 바뀌어 버린 LaTeX 명령어를 되돌린다."""
    for ch, (first, pattern) in _MANGLED_RE.items():
        if ch not in s:
            continue
        out = []
        i = 0
        while True:
            j = s.find(ch, i)
            if j == -1:
                out.append(s[i:])
                break
            out.append(s[i:j])
            m = pattern.match(s, j + 1)
            if m:
                # 백슬래시와 «먹힌 첫 글자»를 함께 되살린다.  TAB+"imes" → "\times"
                out.append("\\" + first + m.group(0))
                i = m.end()
            else:
                out.append(ch)
                i = j + 1
        s = "".join(out)

    # 🔄 프롬프트로 「백슬래시를 두 번 써라」라고 시켰더니 모델이 **줄바꿈에도** 적용해
    #    "\\n" 을 보내왔다. 그러면 진짜 줄바꿈이 아니라 «역슬래시+n» 두 글자가 되어
    #    화면에 그대로 보인다. 뒤에 글자가 안 붙은 \n 만 줄바꿈으로 되돌린다
    #    (\neq 같은 명령어는 뒤에 글자가 붙으므로 건드리지 않는다).
    #    앞에 백슬래시가 하나 더 있으면 LaTeX 의 줄바꿈(\\)이므로 그대로 둔다.
    s = re.sub(r"(?<!\\)\\n(?![a-zA-Z])", "\n", s)
    return s


def _escape_lone_backslashes(s: str) -> str:
    """JSON 이 모르는 이스케이프(\\sqrt 등)의 백슬래시를 두 번으로 만든다."""
    out = []
    i = 0
    while i < len(s):
        c = s[i]
        if c == "\\" and i + 1 < len(s):
            nxt = s[i + 1]
            if nxt in _VALID_JSON_ESCAPE:
                out.append(c)
                out.append(nxt)
                i += 2
                continue
            out.append("\\\\")
            i += 1
            continue
        out.append(c)
        i += 1
    return "".join(out)


def _deep_repair(value):
    if isinstance(value, str):
        return _repair_mangled_latex(value)
    if isinstance(value, list):
        return [_deep_repair(v) for v in value]
    if isinstance(value, dict):
        return {k: _deep_repair(v) for k, v in value.items()}
    return value


def _parse_json_object(content: str) -> dict:
    """응답에서 JSON 객체 하나를 꺼낸다. 못 꺼내면 실패를 실패로 남긴다."""
    fenced = re.search(r"```(?:json)?\s*(.*?)\s*```", content, re.DOTALL)
    candidate = fenced.group(1) if fenced else content

    if not candidate.lstrip().startswith("{"):
        # 앞뒤에 설명 문장이 붙은 경우: 가장 바깥 중괄호 구간만 취한다.
        start, end = candidate.find("{"), candidate.rfind("}")
        if start == -1 or end <= start:
            raise LlmBadResponseException("모델 응답에서 JSON 을 찾지 못했습니다.")
        candidate = candidate[start:end + 1]

    try:
        data = json.loads(candidate)
    except json.JSONDecodeError as first:
        # \sqrt 처럼 JSON 이 모르는 이스케이프 때문일 수 있다. 한 번만 고쳐서 재시도한다.
        try:
            data = json.loads(_escape_lone_backslashes(candidate))
        except json.JSONDecodeError:
            raise LlmBadResponseException(f"모델 응답이 JSON 이 아닙니다: {first}") from first

    if not isinstance(data, dict):
        raise LlmBadResponseException("모델 응답이 객체가 아닙니다.")
    return _deep_repair(data)


# PROBLEMS 표의 제약. docs/DATA_CONTRACT.md §2 와 같이 움직인다.
_OPTIONS_MAX = 1000
_ANSWER_MAX = 200
_REQUIRED = ("question", "problem_type", "answer", "explanation")


def _normalize(data: dict) -> None:
    """
    타입을 계약에 맞춘다.

    🔴 모델이 정답을 숫자로 준다.  "answer": 3  ← 문자열이 아니다
       그러면 라우터의 응답 모델이 거부해 **처리되지 않은 500** 이 난다
       (2026-09-01 실호출에서 3건 중 1건이 이랬다). 여기서 문자열로 맞춘다.
    """
    for k in ("question", "problem_type", "answer", "explanation"):
        v = data.get(k)
        if v is not None and not isinstance(v, str):
            data[k] = str(v)


def _validate(data: dict) -> None:
    missing = [k for k in _REQUIRED if not data.get(k)]
    if missing:
        raise LlmBadResponseException(f"응답에 빠진 항목: {', '.join(missing)}")

    if data.get("problem_type") not in ("MULTIPLE_CHOICE", "SHORT_ANSWER"):
        raise LlmBadResponseException(f"알 수 없는 문제 유형: {data.get('problem_type')}")

    options = data.get("options")
    if options is not None and len(str(options)) > _OPTIONS_MAX:
        raise LlmBadResponseException(
            f"보기가 {_OPTIONS_MAX}자를 넘습니다 ({len(str(options))}자)."
        )

    if len(str(data["answer"])) > _ANSWER_MAX:
        raise LlmBadResponseException(f"정답이 {_ANSWER_MAX}자를 넘습니다.")

    if data["problem_type"] == "MULTIPLE_CHOICE" and str(data["answer"]).strip() not in "1234":
        raise LlmBadResponseException(f"객관식 정답이 보기 번호가 아닙니다: {data['answer']!r}")
