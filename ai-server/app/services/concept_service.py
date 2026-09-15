from langchain_core.messages import HumanMessage

from app.llm_call import ask
from app.vocabulary import grade_name


class ConceptService:
    """
    문제 풀이 후 관련 개념을 설명해주는 서비스
    - 문제 본문 + 학년 + 챕터명을 프롬프트에 주입
    - 모델이 해당 학년 수준에 맞는 개념 설명을 생성
    """

    async def explain(self, question: str, grade: str, chapter_title: str) -> str:
        grade_display = grade_name(grade)

        # 학년, 챕터, 문제를 프롬프트에 주입해 개념 설명 요청
        prompt = f"""당신은 수학 전문 강사입니다.

학생 학년: {grade_display}
단원: {chapter_title}
문제: {question}

위 문제와 관련된 핵심 수학 개념을 {grade_display} 수준에 맞게 설명해주세요.

설명 시 다음을 포함해주세요:
1. 핵심 개념 정의 (쉬운 언어로)
2. 왜 이 개념이 중요한지
3. 실생활 예시

[수식 작성 규칙] 수식이 필요한 경우 반드시 $...$ (인라인) 또는 $$...$$ (블록) 형식으로 감싸야 합니다.
예시: "분수 $\\frac{{3}}{{4}}$", "넓이 $S = \\pi r^2$"

친근하고 격려하는 말투로 작성해주세요."""

        return await ask([HumanMessage(content=prompt)])
