from fastapi import HTTPException, Security
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt

from app.config import settings

security = HTTPBearer()

# 🔴 이 서버는 Spring 이 부르는 «내부 서버» 다. 학생 토큰으로 직접 들어오는 것을 막는다.
#    예전에는 서명과 만료만 봐서 학생이 자기 accessToken 으로 8001 을 직접 부를 수 있었고,
#    로그아웃한 토큰까지 통했다 — 이 서버는 Spring 의 블랙리스트를 모르기 때문이다.
#    반대편 정의: src/main/java/.../global/jwt/TokenType.java
_TYPE_CLAIM = "typ"
_ALLOWED_TYPE = "SERVICE"


def verify_token(
    credentials: HTTPAuthorizationCredentials = Security(security),
) -> str:
    """
    부른 «서비스» 의 이름을 돌려준다.

    ⚠️ 학생이 아니다. 예전 이름이 user_id 여서 「학생 식별자」로 오해를 샀는데,
       실제로는 늘 고정값이었다. 학생 식별자는 요청 바디로 따로 온다.
    """
    token = credentials.credentials
    try:
        payload = jwt.decode(token, settings.jwt_secret, algorithms=["HS256"])
    except JWTError:
        raise HTTPException(status_code=401, detail="Invalid or expired token")

    if payload.get(_TYPE_CLAIM) != _ALLOWED_TYPE:
        # 학생 토큰·리프레시 토큰·종류 없는 옛 토큰이 전부 여기서 막힌다.
        raise HTTPException(status_code=401, detail="Not a service token")

    service = payload.get("sub")
    if not service:
        raise HTTPException(status_code=401, detail="Invalid token")
    return service
