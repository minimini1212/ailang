"""
AI 호출이 «어떻게» 실패했는지가 Spring 까지 살아서 가는가.

🔴 이것이 이 서버의 존재 이유 절반이다. 「한도 초과」·「우리 설정이 틀림」·「응답 형식이
   어긋남」·「서버에 못 닿음」은 학생이 해야 할 일이 각각 다르다 —
   기다린다 / 우리가 고친다 / 다시 시도한다 / 기다린다.
   하나로 뭉치면 학생은 「AI 기능을 이용할 수 없습니다」 한 줄만 보고 아무것도 못 한다.

⚠️ 실제 모델을 부르지 않는다. 클라이언트가 던질 예외를 «직접 만들어» 넣는다.
   반대편 분류: `src/main/java/.../global/exception/ErrorCode.java`
"""

import httpx
import openai
import pytest

import app.llm_call as llm_call
from app.exceptions import (
    LlmAuthException,
    LlmBadResponseException,
    LlmQuotaException,
    LlmUnavailableException,
)
from app.llm import content_to_text


def _응답(status: int) -> httpx.Response:
    return httpx.Response(status, request=httpx.Request("POST", "http://llm.invalid/v1/chat"))


def _요청() -> httpx.Request:
    return httpx.Request("POST", "http://llm.invalid/v1/chat")


class _터지는_클라이언트:
    """`get_llm()` 자리에 끼워 넣어, 정해 둔 예외를 던지게 한다."""

    def __init__(self, 던질_것):
        self.던질_것 = 던질_것

    async def ainvoke(self, messages):
        raise self.던질_것


class _대답하는_클라이언트:
    def __init__(self, content):
        self.content = content

    async def ainvoke(self, messages):
        class _응답객체:
            pass

        r = _응답객체()
        r.content = self.content
        return r


@pytest.fixture
def 클라이언트_바꿔치기(monkeypatch):
    def _설치(가짜):
        monkeypatch.setattr(llm_call, "get_llm", lambda: 가짜)

    return _설치


class Test실패를_네_종류로_나눈다:
    """🔴 잡는 변형: except 가지 하나를 지우거나, 둘을 같은 예외로 합치는 것."""

    async def test_한도_초과는_기다리면_되는_실패다(self, 클라이언트_바꿔치기):
        클라이언트_바꿔치기(_터지는_클라이언트(
            openai.RateLimitError("rate limited", response=_응답(429), body=None)))
        with pytest.raises(LlmQuotaException):
            await llm_call.ask([])

    @pytest.mark.parametrize(
        "터뜨릴_것",
        [
            lambda: openai.AuthenticationError("bad key", response=_응답(401), body=None),
            lambda: openai.PermissionDeniedError("no", response=_응답(403), body=None),
            # 🔴 모델 이름이 틀린 것은 «우리» 설정 문제다. 학생이 다시 눌러도 소용없다.
            lambda: openai.NotFoundError("no such model", response=_응답(404), body=None),
        ],
    )
    async def test_우리가_고쳐야_하는_실패는_따로_분류한다(self, 클라이언트_바꿔치기, 터뜨릴_것):
        클라이언트_바꿔치기(_터지는_클라이언트(터뜨릴_것()))
        with pytest.raises(LlmAuthException):
            await llm_call.ask([])

    async def test_입력이_한도를_넘은_것은_응답_형식_실패로_본다(self, 클라이언트_바꿔치기):
        클라이언트_바꿔치기(_터지는_클라이언트(
            openai.BadRequestError("too long", response=_응답(400), body=None)))
        with pytest.raises(LlmBadResponseException):
            await llm_call.ask([])

    @pytest.mark.parametrize(
        "터뜨릴_것",
        [
            lambda: openai.APITimeoutError(_요청()),
            lambda: openai.APIConnectionError(message="down", request=_요청()),
        ],
    )
    async def test_못_닿은_것은_기다리면_되는_실패다(self, 클라이언트_바꿔치기, 터뜨릴_것):
        클라이언트_바꿔치기(_터지는_클라이언트(터뜨릴_것()))
        with pytest.raises(LlmUnavailableException):
            await llm_call.ask([])

    @pytest.mark.parametrize("상태", [500, 502, 503, 408, 410])
    async def test_서버_쪽_오류와_410_은_못_닿은_것으로_본다(self, 클라이언트_바꿔치기, 상태):
        # 🔴 410 Gone 은 「서비스가 없어졌다」다. 응답 «형식» 문제가 아니다.
        #    2026-09-01 에 실제로 410 을 502 로 보여 주는 바람에 한참 프롬프트를 의심했다.
        #    잡는 변형: 410 을 목록에서 빼는 것.
        클라이언트_바꿔치기(_터지는_클라이언트(
            openai.APIStatusError("gone", response=_응답(상태), body=None)))
        with pytest.raises(LlmUnavailableException):
            await llm_call.ask([])

    @pytest.mark.parametrize("상태", [409, 422])
    async def test_그_밖의_4xx_는_응답_형식_실패다(self, 클라이언트_바꿔치기, 상태):
        클라이언트_바꿔치기(_터지는_클라이언트(
            openai.APIStatusError("nope", response=_응답(상태), body=None)))
        with pytest.raises(LlmBadResponseException):
            await llm_call.ask([])


class Test응답_본문을_문자열로_만들기:
    """⚠️ LangChain 의 content 는 문자열이 아닐 수 있다. 그대로 쓰면 500 이 난다."""

    async def test_평범한_문자열(self, 클라이언트_바꿔치기):
        클라이언트_바꿔치기(_대답하는_클라이언트("안녕하세요"))
        assert await llm_call.ask([]) == "안녕하세요"

    def test_조각_리스트로_와도_이어_붙인다(self):
        assert content_to_text([{"text": "앞"}, {"text": "뒤"}]) == "앞뒤"

    def test_문자열_조각도_섞일_수_있다(self):
        assert content_to_text(["앞", {"text": "뒤"}]) == "앞뒤"

    def test_처음_보는_모양이어도_터지지_않는다(self):
        # 🔴 못 읽는 것과 «죽는» 것은 다르다. 여기서 죽으면 학생은 500 을 본다.
        assert content_to_text(42) == "42"


class Test난이도를_기본값으로_바꿔치기하지_않는다:
    async def test_어휘_밖의_난이도는_거부한다(self):
        # 🔴 조용히 「중간」으로 바꾸면, 프롬프트는 중간 문제를 만드는데 저장은 요청한
        #    난이도로 되어 «학생이 받는 문제와 기록된 난이도가 어긋난다».
        #    그 어긋남이 그대로 다음 문제를 고르는 통계로 들어간다.
        from app.services.problem_service import AiProblemService

        with pytest.raises(LlmBadResponseException):
            await AiProblemService().generate(
                chapter_title="정수와 유리수", difficulty="VERY_HARD", grade="MIDDLE_1")
