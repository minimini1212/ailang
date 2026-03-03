import json
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import HumanMessage
from google.api_core.exceptions import ResourceExhausted
from app.config import settings
from app.exceptions import GeminiQuotaException

# 학년 코드를 한국어 학년명으로 변환
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

DIFFICULTY_DISPLAY = {
    "LOW": "쉬운",
    "MEDIUM": "중간",
    "HIGH": "어려운",
}


class AiProblemService:
    """
    AI 모의문제 생성 서비스
    - 챕터명 + 난이도 + 학년을 프롬프트에 주입
    - Gemini가 해당 조건에 맞는 수학 객관식 문제를 JSON 형식으로 생성
    """

    def __init__(self):
        self.llm = ChatGoogleGenerativeAI(
            model="gemini-2.5-flash",
            google_api_key=settings.gemini_api_key,
        )

    async def generate(self, chapter_title: str, difficulty: str, grade: str) -> dict:
        grade_name = GRADE_DISPLAY.get(grade, grade)
        difficulty_name = DIFFICULTY_DISPLAY.get(difficulty, "중간")

        prompt = f"""당신은 수학 문제 출제 전문가입니다.

학생 학년: {grade_name}
단원: {chapter_title}
난이도: {difficulty_name}

위 조건에 맞는 수학 객관식 문제를 1개 만들어주세요.
반드시 아래 JSON 형식으로만 응답하세요. JSON 외 다른 텍스트는 절대 포함하지 마세요.

{{
  "question": "문제 본문 (수식이나 조건을 명확하게 작성)",
  "problem_type": "MULTIPLE_CHOICE",
  "options": ["보기1", "보기2", "보기3", "보기4"],
  "answer": "정답 번호 (1~4 중 하나의 숫자만)",
  "explanation": "풀이 과정과 정답 해설 (학생이 이해하기 쉽게)"
}}"""

        try:
            response = await self.llm.ainvoke([HumanMessage(content=prompt)])
        except ResourceExhausted:
            raise GeminiQuotaException()
        content = response.content.strip()

        # 마크다운 코드블록(```json ... ```) 제거
        if "```" in content:
            parts = content.split("```")
            content = parts[1]
            if content.startswith("json"):
                content = content[4:]
            content = content.strip()

        data = json.loads(content)

        # options 리스트를 JSON 문자열로 변환 (Spring Boot DB에서 String으로 저장)
        if isinstance(data.get("options"), list):
            data["options"] = json.dumps(data["options"], ensure_ascii=False)

        return data
