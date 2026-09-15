"""
이 서버의 «바깥 경계» 두 가지.

  ① 누가 들어올 수 있나 — 학생 토큰은 막히고, 서비스 토큰만 통과한다
  ② 실패가 어떤 상태 코드로 나가나 — 429 / 502 / 503 이 각각 살아서 나간다

🔴 ②가 무너지면 Spring 이 종류를 되살릴 방법이 없다. 이 서버가 500 하나로 뭉개서
   보내면 반대편에서 아무리 잘 분류해도 이미 정보가 사라진 뒤다.

⚠️ 실제 모델도 Redis 도 부르지 않는다. 서비스를 가짜로 바꿔 끼운다.
"""

import httpx
import pytest
from fastapi.testclient import TestClient
from jose import jwt

import app.routers.problem as problem_router
from app.config import settings
from app.exceptions import (
    LlmAuthException,
    LlmBadResponseException,
    LlmQuotaException,
    LlmUnavailableException,
)
from app.main import app

_문제_요청 = {"chapter_title": "정수와 유리수", "difficulty": "MEDIUM", "grade": "MIDDLE_1"}


def _토큰(typ: str | None, sub: str = "service@internal") -> str:
    payload = {"sub": sub}
    if typ is not None:
        payload["typ"] = typ
    return jwt.encode(payload, settings.jwt_secret, algorithm="HS256")


def _헤더(token: str) -> dict:
    return {"Authorization": f"Bearer {token}"}


@pytest.fixture
def 클라이언트():
    with TestClient(app, raise_server_exceptions=False) as c:
        yield c


class Test들어올_수_있는_것:
    """
    🔴 이 서버는 Spring 이 부르는 «내부 서버» 다. 학생이 직접 부르면 안 된다.
       예전에는 서명과 만료만 봐서, 학생이 자기 accessToken 으로 8001 을 직접 부를 수
       있었고 로그아웃한 토큰까지 통했다.

    🎯 잡는 변형: `verify_token` 의 typ 확인을 지우는 것.
    """

    def test_서비스_토큰은_통과한다(self, 클라이언트, monkeypatch):
        monkeypatch.setattr(problem_router, "_service", _가짜_서비스())
        r = 클라이언트.post("/ai/problem", json=_문제_요청, headers=_헤더(_토큰("SERVICE")))
        assert r.status_code == 200

    @pytest.mark.parametrize("typ", ["ACCESS", "REFRESH"])
    def test_학생_토큰은_막힌다(self, 클라이언트, typ):
        # 서명도 만료도 멀쩡하다. «종류» 만 다르다.
        r = 클라이언트.post("/ai/problem", json=_문제_요청, headers=_헤더(_토큰(typ)))
        assert r.status_code == 401

    def test_종류가_없는_옛_토큰도_막힌다(self, 클라이언트):
        # 🔴 받아 주면 구분이 없던 시절의 토큰이 그대로 통과해 고친 의미가 없어진다.
        r = 클라이언트.post("/ai/problem", json=_문제_요청, headers=_헤더(_토큰(None)))
        assert r.status_code == 401

    def test_서명이_다른_토큰은_막힌다(self, 클라이언트):
        남의_토큰 = jwt.encode({"sub": "x", "typ": "SERVICE"}, "다른-비밀키", algorithm="HS256")
        r = 클라이언트.post("/ai/problem", json=_문제_요청, headers=_헤더(남의_토큰))
        assert r.status_code == 401

    def test_토큰이_아예_없으면_막힌다(self, 클라이언트):
        r = 클라이언트.post("/ai/problem", json=_문제_요청)
        assert r.status_code in (401, 403)

    def test_건강_확인은_토큰_없이_된다(self, 클라이언트):
        assert 클라이언트.get("/health").status_code == 200


class _가짜_서비스:
    """정해 둔 예외를 던지거나, 온전한 문제 하나를 돌려준다."""

    def __init__(self, 던질_것=None):
        self.던질_것 = 던질_것

    async def generate(self, chapter_title: str, difficulty: str, grade: str) -> dict:
        if self.던질_것 is not None:
            raise self.던질_것
        return {
            "question": "$1+1$ 의 값은?",
            "problem_type": "MULTIPLE_CHOICE",
            "options": '["1", "2", "3", "4"]',
            "answer": "2",
            "explanation": "$1+1=2$ 입니다.",
        }


class Test실패가_상태코드로_나간다:
    """
    🔴 잡는 변형: main.py 의 예외 처리기 하나를 지우거나, 두 개를 같은 코드로 합치는 것.
       지우면 FastAPI 가 500 으로 떨어뜨리고, 그 순간 종류는 사라진다.
    """

    @pytest.mark.parametrize(
        "터뜨릴_것, 기대_코드, 왜",
        [
            (LlmQuotaException(), 429, "한도 초과 — 학생은 기다리면 된다"),
            (LlmBadResponseException("형식 어긋남"), 502, "응답 형식 — 다시 시도하면 될 수 있다"),
            (LlmUnavailableException(), 503, "못 닿음 — 기다려야 한다"),
            (LlmAuthException(), 503, "우리 설정 문제 — 학생이 할 수 있는 게 없다"),
        ],
    )
    def test_종류마다_다른_코드로_나간다(self, 클라이언트, monkeypatch, 터뜨릴_것, 기대_코드, 왜):
        monkeypatch.setattr(problem_router, "_service", _가짜_서비스(터뜨릴_것))
        r = 클라이언트.post("/ai/problem", json=_문제_요청, headers=_헤더(_토큰("SERVICE")))
        assert r.status_code == 기대_코드, 왜

    def test_네_종류가_서로_다른_코드를_쓴다(self):
        # 🔴 한도 초과(429)와 나머지가 같아지면 Spring 이 「잠시 후 다시」를 말할 수 없다.
        #    ⚠️ 못 닿음과 설정 문제는 «일부러» 둘 다 503 이다 — 학생이 할 일이 같다.
        from app.main import app as _app

        코드들 = {}
        for 예외, 코드 in [
            (LlmQuotaException, 429),
            (LlmBadResponseException, 502),
            (LlmUnavailableException, 503),
            (LlmAuthException, 503),
        ]:
            assert 예외 in _app.exception_handlers, f"{예외.__name__} 의 처리기가 없다"
            코드들[예외.__name__] = 코드
        assert 코드들["LlmQuotaException"] not in (
            코드들["LlmBadResponseException"],
            코드들["LlmUnavailableException"],
        )


class Test응답_계약:
    def test_보기는_문자열로_나간다(self, 클라이언트, monkeypatch):
        # Spring 쪽 PROBLEMS.OPTIONS 가 문자열 컬럼이다. 리스트로 내보내면 반대편이 깨진다.
        monkeypatch.setattr(problem_router, "_service", _가짜_서비스())
        r = 클라이언트.post("/ai/problem", json=_문제_요청, headers=_헤더(_토큰("SERVICE")))
        assert isinstance(r.json()["options"], str)

    def test_요청에_빠진_항목이_있으면_422(self, 클라이언트):
        r = 클라이언트.post("/ai/problem", json={"chapter_title": "정수와 유리수"},
                            headers=_헤더(_토큰("SERVICE")))
        assert r.status_code == 422
