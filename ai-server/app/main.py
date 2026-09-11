from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from app.exceptions import (
    LlmAuthException,
    LlmBadResponseException,
    LlmQuotaException,
    LlmUnavailableException,
)
from app.routers import chat, concept, problem

app = FastAPI(
    title="AI Lang - AI Server",
    description="수학 튜터 AI 서버 (GitHub Models)",
    version="0.2.0",
)


# 🔴 실패의 «종류»를 여기서 잃지 않는다. 학생이 다시 시도하면 되는지, 기다려야 하는지,
#    우리가 고쳐야 하는지가 각각 다르다 (docs/rules/ai-call-policy.md R3).
@app.exception_handler(LlmQuotaException)
async def quota_handler(request: Request, exc: LlmQuotaException):
    return JSONResponse(
        status_code=429,
        content={"detail": "지금은 이용이 몰리고 있어요. 잠시 후 다시 시도해 주세요."},
    )


@app.exception_handler(LlmAuthException)
async def auth_handler(request: Request, exc: LlmAuthException):
    # 학생 잘못이 아니다. 토큰·권한 문제이므로 우리가 고쳐야 한다.
    return JSONResponse(
        status_code=503,
        content={"detail": "AI 기능을 일시적으로 이용할 수 없습니다."},
    )


@app.exception_handler(LlmUnavailableException)
async def unavailable_handler(request: Request, exc: LlmUnavailableException):
    return JSONResponse(
        status_code=503,
        content={"detail": "AI 서버가 응답하지 않습니다. 잠시 후 다시 시도해 주세요."},
    )


@app.exception_handler(LlmBadResponseException)
async def bad_response_handler(request: Request, exc: LlmBadResponseException):
    return JSONResponse(
        status_code=502,
        content={"detail": "AI 응답을 처리하지 못했습니다. 다시 시도해 주세요."},
    )


# 수학 질문 답변 (챗봇)
app.include_router(chat.router, prefix="/ai", tags=["chat"])

# 문제 풀이 후 개념 설명
app.include_router(concept.router, prefix="/ai", tags=["concept"])

# AI 모의문제 생성
app.include_router(problem.router, prefix="/ai", tags=["problem"])


@app.get("/health")
def health_check():
    return {"status": "ok"}
