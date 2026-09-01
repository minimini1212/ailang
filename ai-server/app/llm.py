"""
LLM 클라이언트를 만드는 유일한 자리.

🔴 다른 곳에서 직접 ChatOpenAI 를 만들지 않는다.
   타임아웃·재시도·모델 선택을 한 곳에서만 강제할 수 있어야 하기 때문이다
   (docs/rules/ai-call-policy.md §3).

🔄 2026-09-01: langchain-google-genai → langchain_openai.
   제공자를 **OpenAI 호환 엔드포인트라면 무엇이든** 되도록 열어 뒀다.
   ⚠️ 처음엔 GitHub Models 를 붙였는데 그 서비스가 이미 종료돼(410 Gone) 못 썼다.
      그래서 주소·모델·키를 전부 설정으로 뺐다 — 다음엔 값만 바꾸면 된다.
   근거와 대가: docs/rules/ai-call-policy.md §0.5
"""

from langchain_openai import ChatOpenAI

from app.config import settings

# 🎯 요청마다 새로 만들지 않는다. 예전엔 요청마다 클라이언트를 만들어
#    커넥션이 회수되지 않았다 (docs/review/ 2026-08-27 리뷰 🟠-16).
_llm: ChatOpenAI | None = None


def get_llm() -> ChatOpenAI:
    global _llm
    if _llm is None:
        _llm = ChatOpenAI(
            model=settings.llm_model,
            api_key=settings.llm_api_key,
            base_url=settings.llm_base_url,
            timeout=settings.llm_timeout_seconds,
            max_retries=settings.llm_max_retries,
        )
    return _llm


def content_to_text(content) -> str:
    """
    응답 본문을 문자열로 만든다.

    ⚠️ LangChain 의 content 는 문자열이 아닐 수 있다(멀티모달 응답에서 리스트).
       그대로 .strip() 하면 AttributeError 로 500 이 난다.
    """
    if isinstance(content, str):
        return content
    if isinstance(content, list):
        parts = []
        for chunk in content:
            if isinstance(chunk, str):
                parts.append(chunk)
            elif isinstance(chunk, dict) and "text" in chunk:
                parts.append(str(chunk["text"]))
        return "".join(parts)
    return str(content)
