from typing import Optional

from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """
    환경변수를 읽는 유일한 자리.

    🔴 필수 필드(기본값 없음)로 두면 값이 없을 때 앱이 아예 안 뜬다.
       읽는 코드가 실제로 있는 값만 필수로 둔다.
    """

    # ── LLM: OpenAI 호환 엔드포인트라면 무엇이든 ──────────────────────
    #
    # 🔴 제공자 이름을 변수명에 넣지 않는다. 2026-09-01 에 GitHub Models 로 갈아탔다가
    #    그 서비스가 이미 종료된 것을 알고 하루 만에 다시 옮겨야 했다.
    #    이름을 provider 중립으로 두면 다음엔 값만 바꾸면 된다.
    #    → docs/rules/ai-call-policy.md §0.5
    #
    # 주소·모델 이름 예시 (셋 다 OpenAI 호환):
    #   Google AI Studio  https://generativelanguage.googleapis.com/v1beta/openai/
    #                     gemini-2.5-flash
    #   Groq              https://api.groq.com/openai/v1
    #                     llama-3.3-70b-versatile
    #   OpenRouter        https://openrouter.ai/api/v1
    #                     <제공자>/<모델>:free
    llm_api_key: str
    llm_base_url: str
    # 🎯 모델 교체는 값이 아니라 결정이므로 ai-call-policy.md 에 근거를 남긴다.
    llm_model: str

    # ⚠️ 무료 등급은 어디든 한도가 낮다. 타임아웃과 재시도를 코드에 박지 않고 여기서 받는다.
    llm_timeout_seconds: float = 60.0
    llm_max_retries: int = 1

    # ── Redis ─────────────────────────────────────────────────────────
    redis_host: str = "ailang-redis"
    redis_port: int = 6379
    # 🔴 Redis 에 비밀번호를 걸면(docker-compose 의 requirepass) 이쪽도 같이 줘야 한다.
    #    안 주면 챗봇 이력 읽기·쓰기가 NOAUTH 로 전부 실패한다.
    #    값이 없으면 None 이고, 그때 redis 클라이언트는 인증 없이 붙는다 — 비밀번호를
    #    아직 안 건 상태와 같다. 「안 걸었다」와 「걸었는데 값을 모른다」를 구분해야 하므로
    #    빈 문자열이 아니라 None 으로 둔다.
    redis_password: Optional[str] = None

    # ── JWT (Spring Boot 와 동일한 시크릿) ────────────────────────────
    jwt_secret: str

    # ── Supabase — ⬜ Phase 3(RAG) 전까지 읽는 코드가 없다 ────────────
    # 🔄 2026-09-01: 필수 → 선택으로 바꿨다. 값이 없어도 앱이 떠야 한다.
    #    예전엔 필수라서, 쓰지도 않는 값이 없다는 이유로 서버 전체가 안 떴다.
    supabase_project_url: Optional[str] = None
    supabase_publishable_secret_key: Optional[str] = None

    class Config:
        env_file = ".env"


settings = Settings()
