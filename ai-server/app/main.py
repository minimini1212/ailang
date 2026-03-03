from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from app.exceptions import GeminiQuotaException
from app.routers import chat, concept, problem

app = FastAPI(
    title="AI Lang - AI Server",
    description="RAG 기반 수학 튜터 AI 서버",
    version="0.1.0",
)

@app.exception_handler(GeminiQuotaException)
async def gemini_quota_handler(request: Request, exc: GeminiQuotaException):
    return JSONResponse(
        status_code=429,
        content={"detail": "AI 서비스 요청 한도를 초과했습니다. 잠시 후 다시 시도해주세요."},
    )

# 수학 질문 답변 (RAG 기반 챗봇)
app.include_router(chat.router, prefix="/ai", tags=["chat"])

# 문제 풀이 후 개념 설명
app.include_router(concept.router, prefix="/ai", tags=["concept"])

# AI 모의문제 생성
app.include_router(problem.router, prefix="/ai", tags=["problem"])


@app.get("/health")
def health_check():
    return {"status": "ok"}
