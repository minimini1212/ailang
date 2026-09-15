"""
검사 공통 준비.

🔴 `app.config.Settings` 는 `llm_api_key` 같은 값을 **필수**로 받는다. 없으면 임포트
   단계에서 죽으므로, 앱 모듈을 불러오기 «전에» 가짜 값을 넣어 둔다.
   ⚠️ 여기 값은 전부 가짜다. 실제 키를 넣지 않는다 — 검사는 절대로 진짜 모델을
      부르지 않는다 (CLAUDE.md 「Never test against a live Gemini call」).
"""

import os
import sys
from pathlib import Path

# ai-server/ 를 임포트 경로에 넣는다. `app.xxx` 로 부를 수 있게 하기 위해서다.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

os.environ.setdefault("LLM_API_KEY", "test-key-not-real")
os.environ.setdefault("LLM_BASE_URL", "http://llm.invalid/v1")
os.environ.setdefault("LLM_MODEL", "test-model")
os.environ.setdefault("JWT_SECRET", "test-secret-not-real-at-least-32-bytes-long")
