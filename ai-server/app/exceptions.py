"""
AI 호출이 실패하는 «방식»을 구분해서 남긴다.

🔴 「한도 초과」·「토큰 문제」·「응답이 형식을 어김」·「서버에 못 닿음」은 서로 다른 일이고,
   학생이 다시 시도해야 하는지 기다려야 하는지가 다르다. 하나로 뭉치면 둘 다 못 한다
   (docs/rules/ai-call-policy.md R3).

🔄 2026-09-01: GeminiQuotaException 하나였던 것을 넷으로 나눴다.
   GitHub Models 무료 한도가 낮아(대략 분당 10건·하루 50건) 한도 초과가
   «드문 일»이 아니라 «흔한 일»이 되기 때문이다.
"""


class LlmError(Exception):
    """AI 호출 실패의 공통 조상."""


class LlmQuotaException(LlmError):
    """요청 한도 초과. 잠시 뒤 다시 하면 된다. → 429"""


class LlmAuthException(LlmError):
    """토큰이 없거나 권한이 없다. 🔴 학생이 아니라 우리가 고칠 일이다. → 503"""


class LlmBadResponseException(LlmError):
    """응답이 형식을 어겼거나 입력이 한도를 넘었다. → 502"""


class LlmUnavailableException(LlmError):
    """모델 서버에 닿지 못했거나 시간 안에 답이 없었다. → 503"""
