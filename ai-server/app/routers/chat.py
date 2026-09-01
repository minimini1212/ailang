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
    # 🔴 학생 식별자. Spring 이 인증된 유저에게서 꺼내 채운다.
    #    ⚠️ 이 값이 대화를 «누구 것으로» 저장할지 정한다. 학생이 직접 보내는 값이 아니다.
    user_id: str


class ChatResponse(BaseModel):
    answer: str
    session_id: str


@router.post("/chat", response_model=ChatResponse)
async def chat(
    request: ChatRequest,
    caller: str = Depends(verify_token),  # 부른 «서비스» 이름 (학생이 아니다)
):
    answer = await _service.answer(
        question=request.question,
        session_id=request.session_id,
        user_id=request.user_id,
    )
    return ChatResponse(answer=answer, session_id=request.session_id)
