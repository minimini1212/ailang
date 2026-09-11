from fastapi import APIRouter, Depends
from pydantic import BaseModel
from app.services.concept_service import ConceptService
from app.services.auth_service import verify_token

router = APIRouter()

# 요청마다 만들지 않는다 (docs/rules/ai-call-policy.md §3).
_service = ConceptService()


class ConceptRequest(BaseModel):
    """Spring Boot에서 전달하는 개념 설명 요청"""
    question: str       # 문제 본문
    grade: str          # 유저 학년 (ELEM_3 ~ HIGH_1)
    chapter_title: str  # 챕터명 (프롬프트 컨텍스트용)


class ConceptResponse(BaseModel):
    """모델이 생성한 개념 설명 응답"""
    concept: str


@router.post("/concept", response_model=ConceptResponse)
async def get_concept(
    request: ConceptRequest,
    caller: str = Depends(verify_token),  # 부른 서비스 이름 (학생이 아니다)
):
    """
    문제 풀이 후 관련 개념 설명 엔드포인트
    - Spring Boot가 문제 정보를 전달하면 모델이 개념 설명을 생성해 반환
    """
    concept = await _service.explain(
        question=request.question,
        grade=request.grade,
        chapter_title=request.chapter_title,
    )
    return ConceptResponse(concept=concept)
