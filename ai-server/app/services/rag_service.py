import redis.asyncio as aioredis
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_core.messages import HumanMessage, AIMessage
from app.config import settings


class RagService:
    def __init__(self):
        self.llm = ChatGoogleGenerativeAI(
            model="gemini-1.5-flash",
            google_api_key=settings.gemini_api_key,
        )
        self.redis = aioredis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            decode_responses=True,
        )

    async def _get_history(self, session_id: str) -> list:
        raw = await self.redis.lrange(f"ailang:chat:{session_id}", 0, -1)
        messages = []
        for i, item in enumerate(raw):
            if i % 2 == 0:
                messages.append(HumanMessage(content=item))
            else:
                messages.append(AIMessage(content=item))
        return messages

    async def _save_history(self, session_id: str, question: str, answer: str):
        key = f"ailang:chat:{session_id}"
        await self.redis.rpush(key, question, answer)
        await self.redis.expire(key, 3600)  # 1시간 TTL

    async def answer(self, question: str, session_id: str, user_id: str) -> str:
        history = await self._get_history(session_id)
        messages = history + [HumanMessage(content=question)]

        # TODO: RAG 검색 결과를 system prompt에 추가 예정
        response = await self.llm.ainvoke(messages)
        answer = response.content

        await self._save_history(session_id, question, answer)
        return answer
