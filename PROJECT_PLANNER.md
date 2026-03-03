# AI와 함께하는 수학 공부 플랫폼 - 프로젝트 계획서

> 작성일: 2026-02-26 | 최종 수정: 2026-02-26
> 대상 학년: 초등 3학년 ~ 고등 1학년
> 데이터 출처: AI Hub 수학 자료

---

## 목차

1. [프로젝트 개요](#1-프로젝트-개요)
2. [시스템 아키텍처](#2-시스템-아키텍처)
3. [기능 요구사항](#3-기능-요구사항)
4. [비기능 요구사항](#4-비기능-요구사항)
5. [ERD](#5-erd)
6. [API 명세서](#6-api-명세서)
7. [RAG 데이터 파이프라인](#7-rag-데이터-파이프라인)
8. [난이도 조정 알고리즘](#8-난이도-조정-알고리즘)
9. [개발 마일스톤](#9-개발-마일스톤)

---

## 1. 프로젝트 개요

### 1.1 프로젝트명
**AI와 함께하는 수학 공부** (ailang)

### 1.2 목적
초3~고1 학생을 대상으로 학년·정답률 기반 맞춤형 문제를 제공하고,
문제 풀이 후 LLM을 통해 관련 개념을 즉시 설명해주는 수학 문제 풀이 플랫폼 구축

### 1.3 핵심 가치
- **맞춤형 문제 제공**: 학년 + 정답률 기반의 자동 난이도 조정
- **즉석 개념 학습**: 문제 풀이 직후 LLM이 관련 수학 개념을 학년 수준에 맞게 설명
- **전문적 AI 챗봇**: RAG 기반으로 수학 질문에 전문 강사 수준의 답변 제공

### 1.4 대상 사용자
| 구분 | 학년 코드 |
|------|----------|
| 초등 3학년 | ELEM_3 |
| 초등 4학년 | ELEM_4 |
| 초등 5학년 | ELEM_5 |
| 초등 6학년 | ELEM_6 |
| 중학 1학년 | MIDDLE_1 |
| 중학 2학년 | MIDDLE_2 |
| 중학 3학년 | MIDDLE_3 |
| 고등 1학년 | HIGH_1 |

---

## 2. 시스템 아키텍처

```
┌─────────────────────────────────────────────────────────┐
│                       Client (Browser)                   │
└───────────────────────┬─────────────────────────────────┘
                        │ HTTP
          ┌─────────────┴──────────────┐
          │                            │
          ▼                            ▼
┌──────────────────┐         ┌──────────────────────┐
│  Spring Boot     │  HTTP   │     FastAPI           │
│  (포트: 8080)    │────────►│  (포트: 8001)         │
│                  │         │                       │
│  - 인증/인가     │         │  - AI 챗봇 (/ai/chat) │
│  - 챕터 관리     │         │  - 개념 설명          │
│  - 문제 관리     │         │    (/ai/concept)      │
│  - 통계 관리     │         │  - JWT 검증           │
│  - AI 연동       │         │  - RAG 검색           │
└────────┬─────────┘         └──────────┬───────────┘
         │                              │
    ┌────┴──────────┐          ┌────────┴──────────┐
    │  Oracle 23ai  │          │  Supabase         │
    │  (포트: 1521) │          │  (pgvector)       │
    │  - 유저       │          │  - 수학 자료       │
    │  - 챕터       │          │    임베딩 벡터     │
    │  - 문제       │          └───────────────────┘
    │  - 통계       │
    └───────────────┘
         │
    ┌────┴──────────┐
    │  Redis        │
    │  (포트: 6380) │
    │  - 챗봇 대화  │
    │    메모리     │
    │  - 이메일인증 │
    │    코드 캐시  │
    └───────────────┘
```

### 2.1 기술 스택

| 영역 | 기술 | 비고 |
|------|------|------|
| Java 백엔드 | Spring Boot 3.x | 인증, 비즈니스 로직 |
| Python AI 서버 | FastAPI + LangChain | AI 챗봇, 개념 설명 |
| LLM | Gemini 1.5 Flash | 답변 생성 |
| Embedding | text-embedding-004 | 벡터 변환 |
| Vector DB | Supabase pgvector | 수학 자료 검색 |
| RDB | Oracle 23ai | 유저, 문제, 통계 |
| Cache | Redis | 대화 메모리, 인증코드 |
| 컨테이너 | Docker Compose | Oracle, Redis, FastAPI |

---

## 3. 기능 요구사항

### 3.1 회원 관리

- [x] 이메일 + 비밀번호 회원가입 (이메일 인증 포함)
- [x] Google OAuth2 소셜 로그인
- [x] JWT 기반 인증 (Access Token + Refresh Token)
- [x] 회원가입 시 학년 선택 (ELEM_3 ~ HIGH_1)
- [x] 동일 이메일 중복 가입 방지 (LOCAL ↔ Google 크로스 차단)

---

### 3.2 문제 풀이 서비스 (핵심)

#### 3.2.1 맞춤형 문제 (정답률 기반)
- 챕터 선택 후 문제 풀기 시작
- 유저의 해당 챕터 정답률에 따라 난이도 자동 조정 (상/중/하)
- 문제 제출 후 정답/오답 + 해설 즉시 반환
- 제출 후 "개념 설명 보기" 버튼 → LLM 개념 설명 별도 요청

#### 3.2.2 랜덤 문제
- 난이도 무관, 챕터 내 랜덤 문제 제공

#### 3.2.3 문제 유형
| 유형 | 설명 |
|------|------|
| 객관식 (4지선다) | options 필드에 JSON 배열로 보기 저장 |
| 단답형 | 숫자 또는 식 직접 입력 |

---

### 3.3 AI 챗봇 서비스

- 수학 관련 자유 질문 입력
- RAG: Supabase pgvector에서 학년 필터 + 유사 자료 검색 → Gemini 프롬프트 주입
- Redis로 대화 맥락 유지 (세션 TTL 1시간)
- 학년에 맞는 수준의 언어로 답변 생성

---

### 3.4 문제 풀이 후 개념 설명 서비스 (LLM 기반)

#### 흐름
```
유저가 문제 제출 → 정답/오답 + 해설 반환
                            ↓
              "개념 설명 보기" 버튼 클릭
                            ↓
     GET /api/problems/{id}/concept (Spring Boot)
                            ↓
     POST /ai/concept (FastAPI) → Gemini 호출
                            ↓
     문제와 연관된 핵심 개념을 학년 수준에 맞게 설명 반환
```

#### 프롬프트 설계 방향
- 학년(grade) + 챕터명(chapter_title) + 문제 본문(question)을 프롬프트에 주입
- 해당 학년 수준에 맞는 언어로 개념 설명
- 왜 이 개념이 중요한지, 실생활 예시 포함

---

## 4. 비기능 요구사항

| 항목 | 요구사항 |
|------|---------|
| 응답 시간 | 일반 API ≤ 500ms, AI API ≤ 5초 |
| 보안 | JWT 인증 필수, CSRF 방어, LOCAL/Google 크로스 차단 |
| 확장성 | 학년/챕터 추가가 DB 조작만으로 가능 |
| 관리자 | 챕터, 문제 CRUD 관리자 API 제공 |

---

## 5. ERD

### 5.1 테이블 목록
```
USERS                - 회원 정보 (grade 포함)
CHAPTERS             - 학년별 챕터(단원)
PROBLEMS             - 문제 (난이도, 유형, 해설 포함)
USER_PROBLEM_HISTORY - 유저 문제 풀이 이력
USER_CHAPTER_STATS   - 유저 챕터별 통계 + 현재 난이도
```

### 5.2 ERD 다이어그램
```
USERS
├── id                  BIGINT (PK, SEQ)
├── email               VARCHAR2(100) UNIQUE NOT NULL
├── password            VARCHAR2(200)
├── nickname            VARCHAR2(50) NOT NULL
├── grade               VARCHAR2(20)            ← ELEM_3 ~ HIGH_1
├── provider            VARCHAR2(20) NOT NULL    (LOCAL / GOOGLE)
├── provider_id         VARCHAR2(100)
├── status              VARCHAR2(20) NOT NULL
├── role                VARCHAR2(20) NOT NULL
├── profile_image_url   VARCHAR2(500)
├── created_at          TIMESTAMP NOT NULL
└── updated_at          TIMESTAMP NOT NULL

CHAPTERS
├── id              BIGINT (PK, SEQ)
├── grade           VARCHAR2(20) NOT NULL
├── title           VARCHAR2(100) NOT NULL
├── description     VARCHAR2(500)
├── order_num       INT NOT NULL
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL

PROBLEMS
├── id              BIGINT (PK, SEQ)
├── chapter_id      BIGINT (FK → CHAPTERS.id) NOT NULL
├── difficulty      VARCHAR2(10) NOT NULL    (HIGH / MEDIUM / LOW)
├── problem_type    VARCHAR2(20) NOT NULL    (MULTIPLE_CHOICE / SHORT_ANSWER)
├── question        CLOB NOT NULL
├── options         VARCHAR2(1000)           (JSON, 객관식 보기 4개)
├── answer          VARCHAR2(200) NOT NULL
├── explanation     CLOB NOT NULL
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL

USER_PROBLEM_HISTORY
├── id              BIGINT (PK, SEQ)
├── user_id         BIGINT (FK → USERS.id) NOT NULL
├── problem_id      BIGINT (FK → PROBLEMS.id) NOT NULL
├── user_answer     VARCHAR2(200) NOT NULL
├── is_correct      NUMBER(1) NOT NULL
├── created_at      TIMESTAMP NOT NULL       ← 풀이 시각
└── updated_at      TIMESTAMP NOT NULL

USER_CHAPTER_STATS
├── id                  BIGINT (PK, SEQ)
├── user_id             BIGINT (FK → USERS.id) NOT NULL
├── chapter_id          BIGINT (FK → CHAPTERS.id) NOT NULL
├── correct_count       INT DEFAULT 0 NOT NULL
├── total_count         INT DEFAULT 0 NOT NULL
├── current_difficulty  VARCHAR2(10) DEFAULT 'MEDIUM' NOT NULL
├── created_at          TIMESTAMP NOT NULL
└── updated_at          TIMESTAMP NOT NULL

UNIQUE: USER_CHAPTER_STATS (user_id, chapter_id)
```

### 5.3 관계 요약
```
USERS 1 ─── N USER_PROBLEM_HISTORY N ─── 1 PROBLEMS
USERS 1 ─── N USER_CHAPTER_STATS   N ─── 1 CHAPTERS
CHAPTERS 1 ─── N PROBLEMS
```

---

## 6. API 명세서

> Spring Boot Base URL: `http://localhost:8080`
> FastAPI Base URL: `http://localhost:8001`
> 인증: `Authorization: Bearer {accessToken}` (Cookie 기반)

> **공통 응답 형식 (Spring Boot)**
> ```json
> {
>   "code": 200,
>   "message": "요청이 성공적으로 처리되었습니다.",
>   "data": { }
> }
> ```
> **공통 에러 응답**
> ```json
> {
>   "code": 400,
>   "message": "에러 메시지",
>   "data": null
> }
> ```

---

### 6.1 인증 API (기구현)

#### POST /api/auth/email/send
> 이메일 인증코드 발송 (회원가입 전 필수)

**Request Body**
```json
{
  "email": "user@example.com"
}
```
**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```

---

#### POST /api/auth/email/verify
> 발송된 인증코드 확인

**Request Body**
```json
{
  "email": "user@example.com",
  "code": "482910"
}
```
**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```
**Error**
| 상태코드 | 메시지 |
|---------|--------|
| 400 | 인증 코드가 만료되었습니다. |
| 400 | 인증 코드가 일치하지 않습니다. |

---

#### POST /api/auth/signup
> 회원가입 (이메일 인증 완료 후 가능)

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123",
  "nickname": "수학왕",
  "grade": "MIDDLE_1"
}
```
- `grade` 허용값: `ELEM_3`, `ELEM_4`, `ELEM_5`, `ELEM_6`, `MIDDLE_1`, `MIDDLE_2`, `MIDDLE_3`, `HIGH_1`

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```
**Error**
| 상태코드 | 메시지 |
|---------|--------|
| 400 | 이메일 인증이 필요합니다. |
| 409 | 이미 가입된 이메일입니다. |

---

#### POST /api/auth/login
> 로그인 (Access Token, Refresh Token을 HttpOnly Cookie로 발급)

**Request Body**
```json
{
  "email": "user@example.com",
  "password": "password123"
}
```
**Response 200**
```
Set-Cookie: access_token={JWT}; HttpOnly; Path=/
Set-Cookie: refresh_token={JWT}; HttpOnly; Path=/
```
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```
**Error**
| 상태코드 | 메시지 |
|---------|--------|
| 400 | 존재하지 않는 회원입니다. |
| 400 | 비밀번호가 틀렸습니다. |

---

#### POST /api/auth/logout
> 로그아웃 (Access Token 블랙리스트 등록, Refresh Token 삭제)

**Request Body** 없음

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```

---

#### POST /api/auth/refresh
> Access Token 재발급 (Refresh Token Cookie 사용)

**Request Body** 없음 (Refresh Token은 Cookie에서 자동 추출)

**Response 200**
```
Set-Cookie: access_token={새 JWT}; HttpOnly; Path=/
Set-Cookie: refresh_token={새 JWT}; HttpOnly; Path=/
```
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```
**Error**
| 상태코드 | 메시지 |
|---------|--------|
| 401 | 리프레시 토큰이 만료되었습니다. |
| 404 | 리프레시 토큰을 찾을 수 없습니다. |

---

#### GET /oauth2/authorization/google
> Google OAuth2 로그인 시작 (브라우저 리다이렉트)

**Response** → Google 로그인 페이지로 리다이렉트
- 성공 시: `http://localhost:5173/oauth2/callback` 로 리다이렉트 (Cookie 발급)
- 실패 시: `http://localhost:5173/oauth2/callback?error={메시지}` 로 리다이렉트

---

### 6.2 유저 API

#### GET /api/users/me
> 내 프로필 조회

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "id": 1,
    "email": "user@example.com",
    "nickname": "수학왕",
    "grade": "MIDDLE_1",
    "profileImageUrl": "https://example.com/profile.jpg",
    "provider": "LOCAL",
    "role": "ROLE_USER"
  }
}
```

---

### 6.3 챕터 API

#### GET /api/chapters
> 내 학년에 해당하는 챕터 목록 조회 (학습 통계 포함)

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": [
    {
      "id": 1,
      "title": "분수",
      "description": "분수의 개념과 사칙연산을 배웁니다.",
      "orderNum": 1,
      "grade": "ELEM_3",
      "myStats": {
        "correctCount": 8,
        "totalCount": 10,
        "currentDifficulty": "HIGH",
        "correctRate": 80.0
      }
    },
    {
      "id": 2,
      "title": "소수",
      "description": "소수의 개념과 연산을 배웁니다.",
      "orderNum": 2,
      "grade": "ELEM_3",
      "myStats": {
        "correctCount": 0,
        "totalCount": 0,
        "currentDifficulty": "MEDIUM",
        "correctRate": 0.0
      }
    }
  ]
}
```
- `myStats.currentDifficulty`: 문제를 한 번도 풀지 않은 경우 기본값 `MEDIUM`

---

### 6.4 문제 API

#### GET /api/problems/adaptive?chapterId={id}
> 정답률 기반 맞춤형 문제 조회 (현재 난이도에 맞는 문제 랜덤 1개)

**Query Parameter**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| chapterId | Long | ✓ | 챕터 ID |

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "id": 42,
    "chapterId": 1,
    "difficulty": "MEDIUM",
    "problemType": "MULTIPLE_CHOICE",
    "question": "3/4 + 1/4 = ?",
    "options": "[\"1/2\", \"1\", \"4/4\", \"2/4\"]",
    "myStats": {
      "currentDifficulty": "MEDIUM",
      "correctRate": 60.0,
      "totalCount": 10
    }
  }
}
```
- `options`: 객관식일 때만 존재, 단답형이면 `null`
- 정답(`answer`)은 보안상 포함하지 않음

**Error**
| 상태코드 | 메시지 |
|---------|--------|
| 404 | 해당 조건에 맞는 문제가 없습니다. |

---

#### GET /api/problems/random?chapterId={id}
> 난이도 무관 랜덤 문제 조회

**Query Parameter**
| 파라미터 | 타입 | 필수 | 설명 |
|---------|------|------|------|
| chapterId | Long | ✓ | 챕터 ID |

**Response 200** (adaptive와 동일 구조)
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "id": 17,
    "chapterId": 1,
    "difficulty": "LOW",
    "problemType": "SHORT_ANSWER",
    "question": "1/2 + 1/2 = ?",
    "options": null,
    "myStats": {
      "currentDifficulty": "MEDIUM",
      "correctRate": 60.0,
      "totalCount": 10
    }
  }
}
```

---

#### POST /api/problems/{id}/submit
> 답안 제출 → 풀이 이력 저장 + 통계 업데이트 + 난이도 재계산

**Path Variable**: `id` = 문제 ID

**Request Body**
```json
{
  "chapterId": 1,
  "userAnswer": "1"
}
```
- 객관식: 보기 번호 (`"1"` ~ `"4"`)
- 단답형: 정답 문자열 직접 입력 (대소문자·앞뒤 공백 무시)

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "isCorrect": true,
    "correctAnswer": "2",
    "explanation": "3/4 + 1/4 = 4/4 = 1 입니다. 분모가 같은 분수끼리는 분자만 더합니다.",
    "updatedDifficulty": "HIGH",
    "updatedCorrectRate": 80.0
  }
}
```
- `updatedDifficulty`: 제출 후 재계산된 현재 난이도
- `updatedCorrectRate`: 제출 후 갱신된 정답률 (%)

**Error**
| 상태코드 | 메시지 |
|---------|--------|
| 404 | 해당 조건에 맞는 문제가 없습니다. |
| 404 | 존재하지 않는 챕터입니다. |

---

#### GET /api/problems/{id}/concept
> 문제 관련 개념 설명 요청 (LLM 생성, 유저 학년 수준에 맞춤)

**Path Variable**: `id` = 문제 ID

**Request Body** 없음

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "concept": "분수의 덧셈에 대해 설명드릴게요!\n\n분수란 전체를 똑같이 나눈 것 중 몇 개를 나타내는 수예요. 예를 들어 피자를 4조각으로 나눴을 때 3조각이 바로 3/4이에요.\n\n분모가 같은 분수끼리 덧셈을 할 때는 분모는 그대로 두고 분자만 더하면 됩니다..."
  }
}
```

---

### 6.5 AI API (FastAPI - `http://localhost:8001`)

#### POST /ai/chat
> 수학 자유 질문 답변 (RAG 기반, 대화 맥락 유지)

**Request Header**: `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "question": "분수의 덧셈은 어떻게 하나요?",
  "session_id": "user-123-session-abc"
}
```
- `session_id`: 대화 세션 식별자 (Redis에 1시간 TTL로 대화 이력 저장)

**Response 200**
```json
{
  "answer": "분수의 덧셈을 설명드릴게요! 분모가 같은 경우에는 분자끼리만 더하면 됩니다...",
  "session_id": "user-123-session-abc"
}
```

---

#### POST /ai/concept
> 문제 관련 개념 설명 (Spring Boot 내부에서 호출, 클라이언트 직접 호출 X)

**Request Header**: `Authorization: Bearer {accessToken}`

**Request Body**
```json
{
  "question": "3/4 + 1/4 = ?",
  "grade": "ELEM_3",
  "chapter_title": "분수"
}
```

**Response 200**
```json
{
  "concept": "분수의 덧셈에 대해 설명드릴게요!..."
}
```

---

#### GET /health
> 서버 상태 확인

**Response 200**
```json
{
  "status": "ok"
}
```

---

### 6.6 관리자 API (인증 필요 + ADMIN 역할)

#### POST /api/admin/chapters
> 챕터 생성

**Request Body**
```json
{
  "grade": "MIDDLE_1",
  "title": "방정식",
  "description": "일차방정식의 개념과 풀이 방법을 배웁니다.",
  "orderNum": 3
}
```
**Response 201**
```json
{
  "code": 201,
  "message": "성공적으로 생성되었습니다.",
  "data": null
}
```

---

#### PUT /api/admin/chapters/{id}
> 챕터 수정

**Request Body**
```json
{
  "title": "일차방정식",
  "description": "수정된 설명",
  "orderNum": 3
}
```
**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```

---

#### DELETE /api/admin/chapters/{id}
> 챕터 삭제

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```

---

#### POST /api/admin/problems
> 문제 생성

**Request Body**
```json
{
  "chapterId": 1,
  "difficulty": "MEDIUM",
  "problemType": "MULTIPLE_CHOICE",
  "question": "3/4 + 1/4 = ?",
  "options": "[\"1/2\", \"1\", \"4/4\", \"2/4\"]",
  "answer": "2",
  "explanation": "분모가 같은 분수끼리는 분자만 더합니다. 3+1=4이므로 4/4=1입니다."
}
```
- `difficulty`: `LOW`, `MEDIUM`, `HIGH`
- `problemType`: `MULTIPLE_CHOICE`, `SHORT_ANSWER`
- `options`: 객관식일 때만 입력, 단답형이면 생략
- `answer`: 객관식은 보기 번호(`"1"`~`"4"`), 단답형은 정답 문자열

**Response 201**
```json
{
  "code": 201,
  "message": "성공적으로 생성되었습니다.",
  "data": null
}
```

---

#### PUT /api/admin/problems/{id}
> 문제 수정

**Request Body** (POST와 동일 구조, 수정할 필드만 포함)
```json
{
  "difficulty": "HIGH",
  "explanation": "수정된 해설 내용"
}
```
**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```

---

#### DELETE /api/admin/problems/{id}
> 문제 삭제

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": null
}
```

---

#### POST /api/admin/rag/upload
> AI Hub 수학 자료를 벡터화하여 Supabase pgvector에 저장

**Request Body** (`multipart/form-data`)
| 필드 | 타입 | 설명 |
|------|------|------|
| file | File | 업로드할 수학 자료 파일 |
| grade | String | 해당 자료의 학년 코드 |
| chapterTitle | String | 챕터명 (메타데이터 태깅용) |

**Response 200**
```json
{
  "code": 200,
  "message": "요청이 성공적으로 처리되었습니다.",
  "data": {
    "uploadedCount": 150
  }
}
```
- `uploadedCount`: 벡터화 후 저장된 청크 수

---

## 7. RAG 데이터 파이프라인

```
AI Hub 수학 자료
      ↓
[전처리] 텍스트 추출, 정제
      ↓
[청킹] 약 500 토큰 단위로 분할
      ↓
[메타데이터 태깅] grade, chapter_title
      ↓
[임베딩] text-embedding-004
      ↓
[저장] Supabase pgvector
      ↓
[검색] 질문 임베딩 → 코사인 유사도 검색 (grade 필터)
      ↓
[프롬프트 주입] 검색 결과 + 질문 → Gemini (챗봇용)
```

---

## 8. 난이도 조정 알고리즘

```
정답률 = correct_count / total_count * 100

if total_count < 3   → 변경 없음 (데이터 부족)
elif 정답률 >= 80    → 난이도 상향 (LOW→MEDIUM, MEDIUM→HIGH)
elif 정답률 < 50     → 난이도 하향 (HIGH→MEDIUM, MEDIUM→LOW)
else                 → 현재 유지
```
- 답안 제출 때마다 실시간 재계산
- 초기 난이도: MEDIUM

---

## 9. 개발 마일스톤

### Phase 1: 기반 완성 ✅
- [x] Spring Boot 인증 (JWT, OAuth2, 이메일)
- [x] FastAPI 기본 구조
- [x] User grade 컬럼 추가
- [x] LOCAL ↔ Google 크로스 중복 가입 방지

### Phase 2: 문제 풀이 핵심 기능 ✅
- [x] CHAPTERS, PROBLEMS, USER_PROBLEM_HISTORY, USER_CHAPTER_STATS 엔티티
- [x] 챕터 목록 API (학년 + 통계 포함)
- [x] 문제 풀기 API (맞춤형, 랜덤)
- [x] 답안 제출 + 난이도 조정
- [x] 개념 설명 API (Spring Boot → FastAPI 연동)

### Phase 3: AI/RAG 완성
- [ ] AI Hub 수학 자료 수집 + 전처리
- [ ] Supabase pgvector 테이블 생성
- [ ] RAG 업로드 파이프라인 스크립트
- [ ] FastAPI rag_service.py RAG 검색 구현 (현재 TODO)
- [ ] 개념 설명 서비스 concept_service.py 구현

### Phase 4: 관리자 + 마무리
- [ ] 관리자 API (챕터/문제 CRUD)
- [ ] 전체 통합 테스트
