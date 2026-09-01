from fastapi import APIRouter, Depends
from pydantic import BaseModel
from app.services.rag_service import RagService
from app.services.auth_service import verify_token

router = APIRouter()

# 🔴 요청마다 만들지 않는다. RagService 는 Redis 커넥션 풀을 들고 있어서
#    매번 새로 만들면 아무도 닫지 않아 연결이 계속 쌓인다.
_service = RagService()


class ChatRequest(BaseModel):
    question: str
    session_id: str


class ChatResponse(BaseModel):
    answer: str
    session_id: str


@router.post("/chat", response_model=ChatResponse)
async def chat(
    request: ChatRequest,
    user_id: str = Depends(verify_token),
):
    answer = await _service.answer(
        question=request.question,
        session_id=request.session_id,
        user_id=user_id,
    )
    return ChatResponse(answer=answer, session_id=request.session_id)
