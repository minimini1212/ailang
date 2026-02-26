from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from app.services.rag_service import RagService
from app.services.auth_service import verify_token

router = APIRouter()


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
    rag = RagService()
    answer = await rag.answer(
        question=request.question,
        session_id=request.session_id,
        user_id=user_id,
    )
    return ChatResponse(answer=answer, session_id=request.session_id)
