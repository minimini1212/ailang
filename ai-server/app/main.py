from fastapi import FastAPI
from app.routers import chat

app = FastAPI(
    title="AI Lang - AI Server",
    description="RAG 기반 수학 튜터 AI 서버",
    version="0.1.0",
)

app.include_router(chat.router, prefix="/ai", tags=["chat"])


@app.get("/health")
def health_check():
    return {"status": "ok"}
