"""
모델이 만든 문제를 **저장하기 전에** 계약과 맞춰 보는 자리.

🔴 여기를 통과한 값은 그대로 Spring 을 거쳐 `PROBLEMS` 표에 들어간다. 컬럼 한계를
   넘으면 DB 예외로 500 이 나고, 유형이 어휘 밖이면 학생이 풀 수 없는 문제가 쌓인다.

⚠️ 실제 모델을 부르지 않는다. 전부 dict 를 넣고 예외를 보는 순수 함수다.
"""

import pytest

from app.exceptions import LlmBadResponseException
from app.services.problem_service import _ANSWER_MAX, _OPTIONS_MAX, _normalize, _validate


def 온전한_문제(**바꿀_것) -> dict:
    data = {
        "question": "$1+1$ 의 값은?",
        "problem_type": "MULTIPLE_CHOICE",
        "options": '["1", "2", "3", "4"]',
        "answer": "2",
        "explanation": "$1+1=2$ 입니다.",
    }
    data.update(바꿀_것)
    return data


class Test타입_맞추기:
    def test_정답을_숫자로_주면_문자열로_바꾼다(self):
        # 🔴 2026-09-01 실호출에서 3건 중 1건이 이랬다. 그대로 두면 라우터의 응답
        #    모델이 거부해 «처리되지 않은 500» 이 난다.
        #    잡는 변형: _normalize 를 지우거나 "answer" 를 목록에서 빼는 것.
        data = 온전한_문제(answer=3)
        _normalize(data)
        assert data["answer"] == "3"

    def test_이미_문자열이면_건드리지_않는다(self):
        data = 온전한_문제(answer="3")
        _normalize(data)
        assert data["answer"] == "3"


class Test빠진_항목:
    @pytest.mark.parametrize("빠뜨릴_것", ["question", "problem_type", "answer", "explanation"])
    def test_필수_항목이_비면_거부한다(self, 빠뜨릴_것):
        # 🔴 빈 값을 그냥 저장하면 학생이 «본문 없는 문제» 를 받는다.
        with pytest.raises(LlmBadResponseException):
            _validate(온전한_문제(**{빠뜨릴_것: ""}))


class Test어휘:
    def test_모르는_문제_유형은_거부한다(self):
        # 🔴 「모른다」를 기본값으로 접지 않는다. 어휘는 DATA_CONTRACT 가 정한다.
        with pytest.raises(LlmBadResponseException):
            _validate(온전한_문제(problem_type="ESSAY"))

    def test_단답형은_보기_번호를_요구하지_않는다(self):
        _validate(온전한_문제(problem_type="SHORT_ANSWER", answer="무리수", options=None))


class Test컬럼_한계:
    def test_보기가_한계를_넘으면_거부한다(self):
        # 🔴 PROBLEMS.OPTIONS 는 VARCHAR2(1000) 이다. LaTeX 섞인 보기 4개가 넘길 수 있고,
        #    넘치면 저장 시점에 DB 예외 → 학생에게 500 이다. 여기서 막는다.
        with pytest.raises(LlmBadResponseException):
            _validate(온전한_문제(options="가" * (_OPTIONS_MAX + 1)))

    def test_한계까지는_통과한다(self):
        # 경계에서 한 칸 틀리면 멀쩡한 문제가 버려진다.
        _validate(온전한_문제(problem_type="SHORT_ANSWER", answer="답", options="가" * _OPTIONS_MAX))

    def test_정답이_한계를_넘으면_거부한다(self):
        with pytest.raises(LlmBadResponseException):
            _validate(온전한_문제(problem_type="SHORT_ANSWER", answer="가" * (_ANSWER_MAX + 1)))


class Test객관식_정답은_보기_번호여야_한다:
    """
    🔴 객관식 채점은 「학생이 고른 번호」와 「정답」을 맞춰 본다. 정답 칸에 번호가 아닌
       것이 들어가면 **학생이 무엇을 골라도 오답**이 된다.

    ⚠️ 이것은 가정이 아니다. 적재된 기출 500문제 중 14문제가 실제로 그 상태다
       (2026-09-15 실측, TODOS 0절). AI 가 만든 문제까지 같아지면 안 된다.
    """

    @pytest.mark.parametrize("번호", ["1", "2", "3", "4"])
    def test_보기_번호는_통과한다(self, 번호):
        _validate(온전한_문제(answer=번호))

    def test_앞뒤_공백은_봐준다(self):
        _validate(온전한_문제(answer=" 2 "))

    def test_풀이가_들어오면_거부한다(self):
        with pytest.raises(LlmBadResponseException):
            _validate(온전한_문제(answer="① 어떤 수를 $a$ 라 하면 $a=2$ 이다"))

    @pytest.mark.parametrize("붙은_번호", ["12", "23", "34", "1234", "123"])
    def test_번호를_이어_붙인_값은_거부한다(self, 붙은_번호):
        # 🔴 잡는 변형: `answer.strip() not in ("1","2","3","4")` 를
        #    `answer.strip() not in "1234"` 로 되돌리는 것.
        #    뒤엣것은 «부분 문자열» 검사라 "12" 도 "1234" 도 통과한다 —
        #    보기가 4개인데 정답이 "12" 인 문제가 저장되고, 학생은 절대 못 맞힌다.
        with pytest.raises(LlmBadResponseException):
            _validate(온전한_문제(answer=붙은_번호))

    @pytest.mark.parametrize("번호", ["0", "5", "①"])
    def test_보기_범위_밖은_거부한다(self, 번호):
        with pytest.raises(LlmBadResponseException):
            _validate(온전한_문제(answer=번호))
