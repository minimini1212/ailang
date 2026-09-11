"""
LLM 을 실제로 부르는 유일한 자리.

🔴 세 서비스가 각자 try/except 를 갖고 있으면 실패 분류가 세 곳에서 갈린다.
   여기 하나만 통과하게 한다 (docs/rules/ai-call-policy.md §3).
"""

import openai
from langchain_core.messages import BaseMessage

from app.exceptions import (
    LlmAuthException,
    LlmBadResponseException,
    LlmQuotaException,
    LlmUnavailableException,
)
from app.llm import content_to_text, get_llm


async def ask(messages: list[BaseMessage]) -> str:
    """
    메시지를 넘기고 답을 문자열로 받는다.

    ⚠️ 여기서 잡는 예외는 전부 openai 패키지의 것이다. GitHub Models 가
       OpenAI 호환 규격이라 클라이언트도 같은 예외를 던진다.
       🎯 예전 Gemini 경로는 langchain 이 예외를 감쌀 수 있어 「429 가 정말 429 로
          나오는가」를 아무도 확인하지 못했다. 이제는 타입이 명확하다.
    """
    try:
        response = await get_llm().ainvoke(messages)
    except openai.RateLimitError as e:
        raise LlmQuotaException() from e
    except openai.AuthenticationError as e:
        raise LlmAuthException() from e
    except openai.PermissionDeniedError as e:
        raise LlmAuthException() from e
    except openai.BadRequestError as e:
        # 입력이 8K 토큰을 넘었을 때도 여기로 온다.
        raise LlmBadResponseException(str(e)) from e
    except (openai.APITimeoutError, openai.APIConnectionError) as e:
        raise LlmUnavailableException() from e
    except openai.NotFoundError as e:
        # 모델 이름이 틀렸거나 그 제공자에 없는 모델이다. 우리 설정 문제다.
        raise LlmAuthException() from e
    except openai.APIStatusError as e:
        # 위에서 안 걸린 나머지 HTTP 오류.
        # 🔴 410 Gone 은 「서비스가 없어졌다」는 뜻이라 응답 형식 문제가 아니다.
        #    2026-09-01 에 GitHub Models 가 410 을 돌려줬는데 502 로 보여서
        #    한참 프롬프트를 의심했다. 사람이 고쳐야 하는 일은 그렇게 말해야 한다.
        if e.status_code >= 500 or e.status_code in (408, 410):
            raise LlmUnavailableException() from e
        raise LlmBadResponseException(str(e)) from e

    return content_to_text(response.content)
