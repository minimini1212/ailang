"""
모델 응답에서 JSON 을 꺼내고, 깨진 수식을 되살리는 자리.

🔴 **모델 응답은 믿을 수 없는 입력이다.** 코드블록으로 감싸 오기도 하고, 앞뒤에 설명을
   달기도 하고, 아예 JSON 이 아닐 때도 있다. 여기가 뚫리면 학생에게 500 이 간다.

🎯 이 파일이 잡는 변형은 전부 «조용히 틀리는» 것들이다. 파싱이 실패하면 눈에 띄지만,
   수식이 한 글자 깨지는 것은 아무도 모른 채 학생 화면까지 간다.

⚠️ 실제 모델을 부르지 않는다. 여기 있는 것은 전부 문자열을 넣고 문자열을 받는
   순수 함수다 — 네트워크도 DB 도 필요 없다.
"""

import json

import pytest

from app.exceptions import LlmBadResponseException
from app.services.problem_service import (
    _escape_lone_backslashes,
    _parse_json_object,
    _repair_mangled_latex,
)

_ONE = '{"question": "1+1?", "problem_type": "MULTIPLE_CHOICE", "answer": "2", "explanation": "둘"}'


class Test응답에서_JSON_꺼내기:
    """🔴 잡는 변형: 코드블록 처리를 «앞에서부터 두 번째 조각을 집는» 옛 방식으로 되돌리는 것."""

    def test_맨몸_JSON(self):
        assert _parse_json_object(_ONE)["answer"] == "2"

    def test_코드블록으로_감싸_와도_꺼낸다(self):
        assert _parse_json_object(f"```json\n{_ONE}\n```")["answer"] == "2"

    def test_언어표시_없는_코드블록도_꺼낸다(self):
        assert _parse_json_object(f"```\n{_ONE}\n```")["answer"] == "2"

    def test_앞뒤에_설명_문장이_붙어도_꺼낸다(self):
        content = f"네, 문제를 만들었습니다!\n\n{_ONE}\n\n도움이 되었길 바랍니다."
        assert _parse_json_object(content)["answer"] == "2"

    def test_코드블록이_둘이면_첫_번째를_집는다(self):
        # 🔴 옛 코드는 ``` 로 쪼갠 뒤 [1] 을 집었다. 블록이 둘이면 엉뚱한 것을 집었다.
        wrong = '{"question": "이건 예시입니다", "problem_type": "SHORT_ANSWER", "answer": "X", "explanation": "예시"}'
        content = f"```json\n{_ONE}\n```\n\n참고 예시:\n```json\n{wrong}\n```"
        assert _parse_json_object(content)["answer"] == "2"

    def test_JSON_이_아니면_실패를_실패로_남긴다(self):
        # 🔴 조용히 빈 dict 를 돌려주면 안 된다. 「모른다」를 「없다」로 접는 것이다.
        with pytest.raises(LlmBadResponseException):
            _parse_json_object("죄송합니다. 문제를 만들 수 없습니다.")

    def test_객체가_아니면_거부한다(self):
        with pytest.raises(LlmBadResponseException):
            _parse_json_object("[1, 2, 3]")


class Test제어문자로_깨진_수식_되살리기:
    """
    🔴 JSON 에서 `\\t` `\\n` `\\r` `\\f` `\\b` 는 제어문자 이스케이프다. 모델이 LaTeX 를
       백슬래시 하나로 쓰면 **명령어의 첫 글자가 먹힌다.**

           모델이 낸 것   "$180 \\times x$"
           json.loads 뒤  "$180 <TAB>imes x$"     ← \\t 가 탭이 됐다

    🎯 그래서 맞춰야 하는 것은 명령어 전체가 아니라 «첫 글자를 뺀 나머지» 다.
    """

    @pytest.mark.parametrize(
        "깨진_것, 되살릴_것",
        [
            ("$180 \times x$", r"$180 \times x$"),
            ("각 \theta 의 크기", r"각 \theta 의 크기"),
            ("\triangle ABC", r"\triangle ABC"),
            ("\frac{3}{4}", r"\frac{3}{4}"),
            ("\rightarrow", r"\rightarrow"),
            ("\bigcirc", r"\bigcirc"),
        ],
    )
    def test_먹힌_첫_글자까지_되살린다(self, 깨진_것, 되살릴_것):
        assert _repair_mangled_latex(깨진_것) == 되살릴_것

    def test_줄바꿈_뒤의_평범한_낱말은_건드리지_않는다(self):
        # 🔴 이것이 이 함수의 «첫 판이 틀렸던» 자리다. 명령어 «전체» 로 맞췄더니
        #    줄바꿈 + "number" 가 \nu + "mber" 로 바뀌었다.
        #    잡는 변형: _MANGLED 의 나머지 목록에 "u" 나 "e" 같은 한 글자를 넣는 것.
        assert _repair_mangled_latex("\nnumber") == "\nnumber"
        assert _repair_mangled_latex("\neverything") == "\neverything"

    def test_이스케이프가_두_번_된_줄바꿈은_진짜_줄바꿈으로(self):
        # 🔄 「백슬래시를 두 번 써라」라고 시켰더니 모델이 줄바꿈에도 적용해서 왔다.
        #    그대로 두면 화면에 «역슬래시 n» 두 글자가 그냥 보인다.
        assert _repair_mangled_latex(r"첫째 줄\n둘째 줄") == "첫째 줄\n둘째 줄"

    def test_뒤에_글자가_붙은_명령어는_줄바꿈으로_바꾸지_않는다(self):
        # \neq 는 명령어다. 위 규칙이 이것까지 먹으면 「같지 않다」가 사라진다.
        assert _repair_mangled_latex(r"$a \neq b$") == r"$a \neq b$"

    def test_LaTeX_의_줄바꿈_두_개는_그대로_둔다(self):
        assert _repair_mangled_latex(r"첫 줄\\n") == "첫 줄\\\\n"


class Test홀로_있는_백슬래시_고치기:
    """
    🔴 `\\sqrt` `\\pi` 처럼 JSON 이 «모르는» 이스케이프는 제어문자로 바뀌는 게 아니라
       **파싱 자체가 실패한다.** 한 번 고쳐서 다시 시도한다.
    """

    def test_모르는_이스케이프는_두_번으로_만든다(self):
        assert _escape_lone_backslashes(r'"\sqrt{2}"') == r'"\\sqrt{2}"'

    def test_JSON_이_아는_이스케이프는_그대로_둔다(self):
        # 🔴 이것까지 두 번으로 만들면 멀쩡한 줄바꿈이 «역슬래시 n» 두 글자가 된다.
        assert _escape_lone_backslashes(r'"줄\n바꿈"') == r'"줄\n바꿈"'
        assert _escape_lone_backslashes(r'"따옴표 \" 안"') == r'"따옴표 \" 안"'

    def test_파싱이_한_번_실패해도_고쳐서_살려낸다(self):
        content = '{"question": "$\\sqrt{2}$ 는?", "problem_type": "SHORT_ANSWER", "answer": "무리수", "explanation": "설명"}'
        # 이대로는 json.loads 가 실패한다 — 그것이 이 검사의 전제다.
        with pytest.raises(json.JSONDecodeError):
            json.loads(content)
        assert _parse_json_object(content)["answer"] == "무리수"
