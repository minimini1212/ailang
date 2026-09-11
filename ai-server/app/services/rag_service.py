import redis.asyncio as aioredis
from langchain_core.messages import AIMessage, HumanMessage, SystemMessage

from app.config import settings
from app.llm_call import ask

# 🔴 GitHub Models 무료 한도는 입력 8K 토큰이다. 이력에 상한이 없으면
#    대화가 길어질수록 매 턴 전량을 다시 보내다가 반드시 한도를 넘는다.
#    Redis 리스트에 남기는 항목 수(질문·답변 각각 1개씩 세어) 상한.
_MAX_HISTORY_ITEMS = 20  # 질문+답변 10쌍

# 🎯 다른 두 서비스는 「당신은 수학 전문 강사입니다」로 시작하는데 챗봇만
#    학생 문장을 그대로 모델에 넘기고 있었다. 과목·수준·말투 제한이 없으면
#    초3 학생이 쓰는 범용 챗봇이 된다.
_SYSTEM_PROMPT = """당신은 초등학교 3학년부터 고등학교 1학년 학생을 가르치는 수학 선생님입니다.

- 수학과 학습에 관한 질문에만 답합니다. 그 밖의 주제는 정중히 돌려보냅니다.
- 학생이 이해할 수 있는 쉬운 말로, 친근하고 격려하는 말투로 답합니다.
- 답을 바로 주기보다 풀이 과정을 함께 따라가게 합니다.
- 수식은 $...$ (인라인) 또는 $$...$$ (블록) 형식으로 감쌉니다.
- 개인정보(이름·연락처·주소 등)를 묻지 않고, 학생이 적더라도 답에 옮기지 않습니다."""


class RagService:
    """
    수학 자유 질문 챗봇.

    ⚠️ 이름이 RagService 지만 아직 RAG 검색은 없다 (Phase 3).
       지금은 대화 이력만 얹어 모델에 그대로 묻는다.
    """

    def __init__(self):
        self.redis = aioredis.Redis(
            host=settings.redis_host,
            port=settings.redis_port,
            decode_responses=True,
        )

    @staticmethod
    def _key(session_id: str, user_id: str) -> str:
        # 🔴 세션 키에 반드시 학생을 넣는다. 예전엔 클라이언트가 준 session_id 만 써서
        #    남의 session_id 를 넣으면 남의 대화가 답변 맥락에 실렸다.
        #
        # 🔄 2026-09-01: 이제 진짜로 나뉜다.
        #    한동안 여기 user_id 는 토큰의 subject 였고 그건 모든 학생에게 같은 고정값
        #    ("service@internal")이었다. 즉 키를 바꿔도 하이재킹이 그대로 남는 상태였다.
        #    Spring 의 AiChatController 가 인증된 학생 id 를 실어 보내도록 고쳐서 닫혔다.
        return f"ailang:chat:{user_id}:{session_id}"

    async def _get_history(self, key: str) -> list:
        raw = await self.redis.lrange(key, 0, -1)
        messages = []
        for i, item in enumerate(raw):
            messages.append(HumanMessage(content=item) if i % 2 == 0 else AIMessage(content=item))
        return messages

    async def _save_history(self, key: str, question: str, answer: str):
        pipe = self.redis.pipeline()
        pipe.rpush(key, question, answer)
        # 오래된 항목부터 버려 상한을 유지한다.
        pipe.ltrim(key, -_MAX_HISTORY_ITEMS, -1)
        # 🎯 rpush 와 expire 를 따로 await 하면 그 사이에 죽었을 때 TTL 없는 키가
        #    영구히 남는다. 한 번에 보낸다.
        pipe.expire(key, 3600)
        await pipe.execute()

    async def answer(self, question: str, session_id: str, user_id: str) -> str:
        key = self._key(session_id, user_id)
        history = await self._get_history(key)

        # TODO: RAG 검색 결과를 system prompt 뒤에 덧붙일 예정 (Phase 3)
        messages = [SystemMessage(content=_SYSTEM_PROMPT), *history, HumanMessage(content=question)]

        answer = await ask(messages)
        await self._save_history(key, question, answer)
        return answer
