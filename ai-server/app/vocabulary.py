"""
학년·난이도 어휘를 사람이 읽는 말로 바꾸는 유일한 자리.

🔴 **여기 있는 값은 정본이 아니다.** 정본은 Spring 의 `Grade` · `Difficulty` enum 이다
   (`docs/DATA_CONTRACT.md` §3). 이 파일은 그 어휘를 «베껴 둔» 것이고, 베낀 것은 낡는다.

🔴 **왜 모았나.** 2026-09-16 실측: 학년 여덟 값이 다섯 곳에 각각 적혀 있었고 그중 둘이
   이 서버 안(`concept_service.py` · `problem_service.py`)의 **서로 다른 복사본**이었다.
   같은 서버 안에서 두 벌을 들고 있을 이유는 없어서 한 곳으로 합쳤다.
   ⚠️ 남은 복사본은 **자바 enum 1 · 여기 1 · 화면 2 = 넷**이다. 자바와 여기를 합치는 것은
   「FastAPI 가 학년명을 Spring 에게서 받을지」를 정해야 하는 일이라 열어 뒀다
   (`TODOS.md` 4절).

🔴 **모르는 값을 기본값으로 접지 않는다.** 이 파일이 존재하는 두 번째 이유다 —
   아래 「왜 거부하나」 참고.
"""

from app.exceptions import LlmBadResponseException

# 학년 코드 → 사람이 읽는 이름
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

# 난이도 코드 → 프롬프트에 쓰는 말
DIFFICULTY_DISPLAY = {
    "LOW": "쉬운",
    "MEDIUM": "중간",
    "HIGH": "어려운",
}


def grade_name(code: str) -> str:
    """
    학년 코드를 사람이 읽는 이름으로. <b>모르는 값이면 거부한다.</b>

    🔴 **왜 거부하나 — 예전에는 `GRADE_DISPLAY.get(code, code)` 였다.**
    못 찾으면 학년 «코드» 를 그대로 돌려줬고, 그러면 이런 프롬프트가 모델에게 갔다:

    ```
    학생 학년: MIDDLE_4
    위 문제와 관련된 핵심 개념을 MIDDLE_4 수준에 맞게 설명해 주세요.
    ```

    모델은 그 말을 **알아들은 척 답한다.** 에러는 어디서도 안 나고, 학생은 자기 수준과
    맞지 않는 설명을 받는다. 🎯 **조용히 틀리는 쪽이라 아무도 모른다.**

    ⚠️ 바로 아래 `difficulty_name` 은 처음부터 거부하고 있었다. 같은 파일 두 줄 차이로
    정책이 반대였던 셈이라, 2026-09-16 에 학년 쪽을 맞췄다.

    ⚠️ 여기서 나는 실패는 «학생 탓이 아니다». Spring 은 enum 에서 꺼낸 값만 보내므로,
    이 예외가 뜬다는 것은 **두 서버의 어휘가 어긋났다**는 뜻이다 — 우리가 고칠 일이다.
    """
    if code not in GRADE_DISPLAY:
        raise LlmBadResponseException(f"알 수 없는 학년: {code!r}")
    return GRADE_DISPLAY[code]


def difficulty_name(code: str) -> str:
    """
    난이도 코드를 프롬프트에 쓰는 말로. <b>모르는 값이면 거부한다.</b>

    🔴 조용히 「중간」으로 바꿔치기하면 **프롬프트는 중간 문제를 만드는데 저장은 요청한
    난이도로 되어 어긋난다.** 그 어긋남이 그대로 학생의 통계에 들어가고, 통계가 다음
    문제를 고른다 (`docs/rules/ai-call-policy.md`).
    """
    if code not in DIFFICULTY_DISPLAY:
        raise LlmBadResponseException(f"알 수 없는 난이도: {code!r}")
    return DIFFICULTY_DISPLAY[code]
