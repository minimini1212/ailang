from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import HumanMessage
from google.api_core.exceptions import ResourceExhausted
from app.config import settings
from app.exceptions import GeminiQuotaException

# 학년 코드를 한국어 학년명으로 변환하는 매핑
GRADE_DISPLAY = {
    "ELEM_3": "초등학교 3학년",
    "ELEM_4": "초등학교 4학년",
    "ELEM_5": "초등학교 5학년",
    "ELEM_6": "초등학교 6학년",
    "MIDDLE_1": "중학교 1학년",
    "MIDDLE_2": "중학교 2학년",
    "MIDDLE_3": "중학교 3학년",
    "HIGH_1": "고등학교 1학년",
}


class ConceptService:
    """
    문제 풀이 후 관련 개념을 설명해주는 서비스
    - 문제 본문 + 학년 + 챕터명을 프롬프트에 주입
    - Gemini가 해당 학년 수준에 맞는 개념 설명을 생성
    """

    def __init__(self):
        self.llm = ChatGoogleGenerativeAI(
            model="gemini-2.5-flash",
            google_api_key=settings.gemini_api_key,
        )

    async def explain(self, question: str, grade: str, chapter_title: str) -> str:
        grade_name = GRADE_DISPLAY.get(grade, grade)

        # 학년, 챕터, 문제를 프롬프트에 주입해 개념 설명 요청
        prompt = f"""당신은 수학 전문 강사입니다.

학생 학년: {grade_name}
단원: {chapter_title}
문제: {question}

위 문제와 관련된 핵심 수학 개념을 {grade_name} 수준에 맞게 설명해주세요.

설명 시 다음을 포함해주세요:
1. 핵심 개념 정의 (쉬운 언어로)
2. 왜 이 개념이 중요한지
3. 실생활 예시

[수식 작성 규칙] 수식이 필요한 경우 반드시 $...$ (인라인) 또는 $$...$$ (블록) 형식으로 감싸야 합니다.
예시: "분수 $\\frac{{3}}{{4}}$", "넓이 $S = \\pi r^2$"

친근하고 격려하는 말투로 작성해주세요."""

        try:
            response = await self.llm.ainvoke([HumanMessage(content=prompt)])
        except ResourceExhausted:
            raise GeminiQuotaException()
        return response.content
