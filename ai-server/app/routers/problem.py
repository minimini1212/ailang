from fastapi import APIRouter, Depends
from pydantic import BaseModel
from typing import Optional
from app.services.problem_service import AiProblemService
from app.services.auth_service import verify_token

router = APIRouter()

# 요청마다 만들지 않는다 (docs/rules/ai-call-policy.md §3).
_service = AiProblemService()


class AiProblemRequest(BaseModel):
    """Spring Boot에서 전달하는 모의문제 생성 요청"""
    chapter_title: str   # 챕터명 (예: "방정식과 부등식")
    difficulty: str      # 난이도: LOW / MEDIUM / HIGH
    grade: str           # 학년: ELEM_3 ~ HIGH_1


class AiProblemResponse(BaseModel):
    """모델이 생성한 모의문제 응답"""
    question: str
    problem_type: str
    options: Optional[str] = None   # 객관식 보기 JSON 문자열 (단답형이면 null)
    answer: str
    explanation: str


@router.post("/problem", response_model=AiProblemResponse)
async def generate_ai_problem(
    request: AiProblemRequest,
    caller: str = Depends(verify_token),  # 부른 서비스 이름 (학생이 아니다)
):
    """
    AI 모의문제 생성 엔드포인트
    - Spring Boot가 챕터명, 난이도, 학년을 전달
    - 모델이 해당 조건에 맞는 수학 문제를 생성해 반환
    """
    data = await _service.generate(
        chapter_title=request.chapter_title,
        difficulty=request.difficulty,
        grade=request.grade,
    )
    return AiProblemResponse(**data)
