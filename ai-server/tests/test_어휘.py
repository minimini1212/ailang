"""
학년·난이도 코드를 사람이 읽는 말로 바꾸는 자리.

🔴 **이 서버는 어휘의 «정본» 이 아니다.** 정본은 Spring 의 enum 이고 여기 있는 것은
   베껴 둔 것이다. 그래서 두 서버의 어휘가 어긋날 수 있고, **어긋났을 때 어떻게
   구는지**가 이 파일이 지키는 것이다.

🎯 이 검사들이 잡는 변형은 하나로 요약된다 —
   **「모르는 값을 그냥 통과시키는 것」**. 통과시키면 에러가 안 나고, 그래서 아무도 모른다.

⚠️ 실제 모델을 부르지 않는다. 문자열을 넣고 문자열이나 예외를 받는 순수 함수다.
"""

import pytest

from app.exceptions import LlmBadResponseException
from app.vocabulary import (
    DIFFICULTY_DISPLAY,
    GRADE_DISPLAY,
    difficulty_name,
    grade_name,
)


class Test어휘에_구멍이_없다:
    """🔴 Spring 의 enum 과 값이 맞아야 한다. 어긋나면 그 학년은 프롬프트에서 깨진다."""

    def test_학년은_여덟_개다(self):
        # 초3 ~ 고1. 하나 늘리면 이 검사가 빨간불이 되고, 그때 다섯 곳을 다 고쳤는지
        # 돌아보게 된다 (TODOS 4절 「학년 어휘가 다섯 곳에 있다」).
        assert len(GRADE_DISPLAY) == 8

    @pytest.mark.parametrize(
        "코드",
        ["ELEM_3", "ELEM_4", "ELEM_5", "ELEM_6",
         "MIDDLE_1", "MIDDLE_2", "MIDDLE_3", "HIGH_1"],
    )
    def test_여덟_학년이_모두_이름을_가진다(self, 코드):
        assert grade_name(코드).strip()

    def test_난이도는_셋이다(self):
        assert len(DIFFICULTY_DISPLAY) == 3

    @pytest.mark.parametrize("코드", ["LOW", "MEDIUM", "HIGH"])
    def test_세_난이도가_모두_이름을_가진다(self, 코드):
        assert difficulty_name(코드).strip()

    def test_이름이_서로_겹치지_않는다(self):
        # 겹치면 프롬프트만 보고는 어느 학년인지 알 수 없다.
        assert len(set(GRADE_DISPLAY.values())) == len(GRADE_DISPLAY)


class Test모르는_학년은_거부한다:
    """
    🔴 **2026-09-16 에 고친 자리다.** 예전에는 `GRADE_DISPLAY.get(code, code)` 였다.

    ```
    못 찾으면 학년 «코드» 를 그대로 돌려줬다
      → 프롬프트에 「학생 학년: MIDDLE_4」 가 실린다
      → 모델은 «알아들은 척» 답한다
      → 에러는 어디서도 안 나고, 학생은 자기 수준과 안 맞는 설명을 받는다
    ```

    🎯 잡는 변형: `raise` 를 지우고 `.get(code, code)` 로 되돌리는 것.
    """

    @pytest.mark.parametrize(
        "모르는_값",
        ["MIDDLE_4", "ELEM_2", "HIGH_2", "중1", "middle_1", "GRADE_7", ""],
    )
    def test_어휘_밖의_학년은_거부한다(self, 모르는_값):
        with pytest.raises(LlmBadResponseException):
            grade_name(모르는_값)

    def test_거부할_때_받은_값을_남긴다(self):
        # 🔴 「알 수 없는 학년」만 적으면 무엇이 왔는지 모른다. 두 서버의 어휘가
        #    어긋난 것이므로 «무엇이 왔는지» 가 고칠 단서다.
        with pytest.raises(LlmBadResponseException) as e:
            grade_name("MIDDLE_4")
        assert "MIDDLE_4" in str(e.value)

    def test_소문자는_봐주지_않는다(self):
        # 어휘는 DATA_CONTRACT 가 정한 대문자 그대로만 받는다.
        with pytest.raises(LlmBadResponseException):
            grade_name("middle_1")


class Test모르는_난이도는_거부한다:
    """이쪽은 처음부터 거부하고 있었다. 학년 쪽을 여기에 맞춘 것이다."""

    @pytest.mark.parametrize("모르는_값", ["VERY_HIGH", "중간", "medium", ""])
    def test_어휘_밖의_난이도는_거부한다(self, 모르는_값):
        with pytest.raises(LlmBadResponseException):
            difficulty_name(모르는_값)

    def test_거부할_때_받은_값을_남긴다(self):
        with pytest.raises(LlmBadResponseException) as e:
            difficulty_name("VERY_HIGH")
        assert "VERY_HIGH" in str(e.value)


class Test서비스까지_이어지는가:
    """
    🔴 함수 하나가 거부해도 **부르는 쪽이 안 쓰면 소용없다.** 두 서비스가 실제로
       이 함수를 지나는지 본다.
    """

    async def test_개념_설명은_모르는_학년을_거부한다(self):
        from app.services.concept_service import ConceptService

        with pytest.raises(LlmBadResponseException):
            await ConceptService().explain(
                question="$1+1$ 은?", grade="MIDDLE_4", chapter_title="정수와 유리수")

    async def test_모의문제는_모르는_학년을_거부한다(self):
        from app.services.problem_service import AiProblemService

        with pytest.raises(LlmBadResponseException):
            await AiProblemService().generate(
                chapter_title="정수와 유리수", difficulty="MEDIUM", grade="MIDDLE_4")

    async def test_모의문제는_모르는_난이도도_거부한다(self):
        from app.services.problem_service import AiProblemService

        with pytest.raises(LlmBadResponseException):
            await AiProblemService().generate(
                chapter_title="정수와 유리수", difficulty="VERY_HIGH", grade="MIDDLE_1")

    async def test_학년을_먼저_본다(self):
        # 🎯 둘 다 틀렸을 때 어느 쪽을 말해 주나. 학년을 먼저 보므로 학년을 말한다 —
        #    순서가 바뀌면 사람이 난이도부터 고치려 들게 된다.
        from app.services.problem_service import AiProblemService

        with pytest.raises(LlmBadResponseException) as e:
            await AiProblemService().generate(
                chapter_title="정수와 유리수", difficulty="VERY_HIGH", grade="MIDDLE_4")
        assert "학년" in str(e.value)
