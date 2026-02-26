# AI와 함께하는 수학 공부 플랫폼 - 프로젝트 계획서

> 작성일: 2026-02-26
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
AI Hub에서 제공하는 초3~고1 수학 자료를 기반으로, 학생의 학년에 맞는 맞춤형 수학 학습 경험을 제공하는 플랫폼 구축

### 1.3 핵심 가치
- **맞춤형 학습**: 학년/정답률 기반의 개인화된 콘텐츠 제공
- **전문적 지식 전달**: RAG 기반 AI 챗봇으로 수학 전문 강사 수준의 설명 제공
- **자기주도 학습**: 학생이 능동적으로 학습 내용·문제·챗봇을 선택하여 학습

### 1.4 대상 사용자
| 구분 | 학년 코드 | 설명 |
|------|----------|------|
| 초등 3학년 | ELEM_3 | 분수, 도형, 기초 연산 |
| 초등 4학년 | ELEM_4 | 소수, 혼합 계산 |
| 초등 5학년 | ELEM_5 | 분수 사칙연산, 약수/배수 |
| 초등 6학년 | ELEM_6 | 비율, 원, 정비례/반비례 |
| 중학 1학년 | MIDDLE_1 | 정수/유리수, 방정식, 함수 기초 |
| 중학 2학년 | MIDDLE_2 | 연립방정식, 부등식, 확률 |
| 중학 3학년 | MIDDLE_3 | 이차방정식, 이차함수, 통계 |
| 고등 1학년 | HIGH_1 | 다항식, 방정식/부등식, 집합, 함수 |

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
│  (포트: 8080)    │◄───────►│  (포트: 8001)         │
│                  │  JWT    │                       │
│  - 인증/인가     │         │  - AI 챗봇 (/ai/chat) │
│  - 챕터 관리     │         │  - JWT 검증           │
│  - 문제 관리     │         │  - RAG 검색           │
│  - 통계 관리     │         │  - Gemini 호출        │
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
| Python AI 서버 | FastAPI + LangChain | AI 챗봇, RAG |
| LLM | Gemini 1.5 Flash | 답변 생성 |
| Embedding | text-embedding-004 | 벡터 변환 |
| Vector DB | Supabase pgvector | 수학 자료 검색 |
| RDB | Oracle 23ai | 유저, 문제, 통계 |
| Cache | Redis | 대화 메모리, 인증코드 |
| 컨테이너 | Docker Compose | Oracle, Redis, FastAPI |

---

## 3. 기능 요구사항

### 3.1 회원 관리

#### 3.1.1 회원가입 / 로그인 (기구현)
- [x] 이메일 + 비밀번호 회원가입 (이메일 인증 포함)
- [x] Google OAuth2 소셜 로그인
- [x] JWT 기반 인증 (Access Token + Refresh Token)
- [x] 로그아웃 (Refresh Token 무효화)

#### 3.1.2 학년 설정 (신규)
- [ ] 회원가입 완료 후 학년 선택 단계 추가
- [ ] 마이페이지에서 학년 변경 가능
- [ ] 학년 변경 시 통계 초기화 여부 선택

#### 3.1.3 프로필
- [ ] 닉네임, 학년, 프로필 이미지 조회
- [ ] 닉네임, 학년 수정

---

### 3.2 AI 챗봇 서비스

#### 3.2.1 기본 동작
- 학생이 수학 관련 질문 입력
- FastAPI가 JWT 검증 후 유저의 학년 정보 파악
- Supabase pgvector에서 해당 학년 수준의 관련 수학 자료 검색 (RAG)
- 검색된 자료 + 질문 → Gemini에 전달 → 전문 강사 스타일의 답변 생성
- 답변 반환

#### 3.2.2 답변 스타일 가이드 (프롬프트 설계)
```
- 초등생: 쉬운 언어, 그림/비유 활용, 단계별 설명
- 중학생: 개념 + 풀이 과정, 공식 설명
- 고등생: 개념 + 심화 설명, 문제 연결
- 공통: "왜?"에 대한 이유 설명, 격려 포함
```

#### 3.2.3 대화 메모리 (선택적)
- Redis에 세션별 대화 이력 저장 (TTL: 1시간)
- 이전 질문 맥락을 유지하여 연속 질문 가능
- 미결정: 기능 포함 여부 → **v1에서는 포함 방향 권장** (Redis 이미 구성됨)

---

### 3.3 문제 제공 서비스

#### 3.3.1 맞춤형 문제 (정답률 기반)
- 챕터 선택 후 문제 풀기 시작
- 유저의 해당 챕터 정답률에 따라 난이도 자동 조정 (상/중/하)
- 문제 제출 후 즉시 정답/오답 표시 + 해설 제공
- 오답 시 유사 문제 추가 제공 옵션

#### 3.3.2 랜덤 문제
- 난이도 무관, 챕터 내 랜덤 문제 제공
- 빠른 연습용 모드

#### 3.3.3 문제 유형
| 유형 | 설명 | 예시 |
|------|------|------|
| 객관식 (4지선다) | 보기 4개 중 1개 선택 | "다음 중 소수가 아닌 것은?" |
| 단답형 | 숫자 또는 식 입력 | "3/4 + 1/2 = ?" |

---

### 3.4 학습 내용 제공 서비스

#### 3.4.1 챕터 목록
- 유저 학년에 맞는 챕터 목록 표시
- 챕터별 학습 진도 표시 (학습 완료 여부)

#### 3.4.2 챕터별 학습 내용
- 개념 설명 (텍스트)
- 예제 문제 포함
- 챕터 내 여러 소단원으로 구성

#### 3.4.3 학습 예시 구성 (초3 예시)
```
챕터: 분수
 └── 소단원 1: 분수란 무엇인가?
 └── 소단원 2: 분수의 크기 비교
 └── 소단원 3: 분수의 덧셈
 └── 소단원 4: 분수의 뺄셈
```

---

## 4. 비기능 요구사항

| 항목 | 요구사항 |
|------|---------|
| 응답 시간 | 일반 API ≤ 500ms, AI 챗봇 ≤ 5초 |
| 보안 | JWT 인증 필수, CSRF 방어 적용 (기구현) |
| 확장성 | 학년/챕터 추가가 DB 조작만으로 가능하도록 설계 |
| 관리자 | 챕터, 문제, 학습 내용 CRUD 관리자 API 제공 |
| 로깅 | API 요청/응답 로그, AI 챗봇 질문/답변 로그 |

---

## 5. ERD

### 5.1 테이블 목록
```
USERS               - 회원 정보
CHAPTERS            - 학년별 챕터
LEARNING_CONTENTS   - 챕터별 학습 내용 (소단원)
PROBLEMS            - 문제
USER_PROBLEM_HISTORY - 유저 문제 풀이 이력
USER_CHAPTER_STATS  - 유저 챕터별 통계 (난이도 관리용)
```

### 5.2 ERD 다이어그램
```
USERS
├── id              BIGINT (PK, SEQ)
├── email           VARCHAR2(100) UNIQUE NOT NULL
├── password        VARCHAR2(200)
├── nickname        VARCHAR2(50) NOT NULL
├── grade           VARCHAR2(20)          ← 신규 추가 (ELEM_3~HIGH_1)
├── provider        VARCHAR2(20) NOT NULL (LOCAL/GOOGLE)
├── provider_id     VARCHAR2(100)
├── status          VARCHAR2(20) NOT NULL (ACTIVE/INACTIVE)
├── role            VARCHAR2(20) NOT NULL (USER/ADMIN)
├── profile_image_url VARCHAR2(500)
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL

CHAPTERS
├── id              BIGINT (PK, SEQ)
├── grade           VARCHAR2(20) NOT NULL  (ELEM_3~HIGH_1)
├── title           VARCHAR2(100) NOT NULL (e.g. "분수")
├── description     VARCHAR2(500)
├── order_num       INT NOT NULL           (챕터 순서)
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL

LEARNING_CONTENTS
├── id              BIGINT (PK, SEQ)
├── chapter_id      BIGINT (FK → CHAPTERS.id) NOT NULL
├── title           VARCHAR2(100) NOT NULL (소단원명)
├── body            CLOB NOT NULL          (학습 내용 본문)
├── order_num       INT NOT NULL           (소단원 순서)
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL

PROBLEMS
├── id              BIGINT (PK, SEQ)
├── chapter_id      BIGINT (FK → CHAPTERS.id) NOT NULL
├── difficulty      VARCHAR2(10) NOT NULL  (HIGH/MEDIUM/LOW)
├── problem_type    VARCHAR2(20) NOT NULL  (MULTIPLE_CHOICE/SHORT_ANSWER)
├── question        CLOB NOT NULL          (문제 본문)
├── options         VARCHAR2(1000)         (JSON, 객관식 보기 4개)
├── answer          VARCHAR2(200) NOT NULL
├── explanation     CLOB NOT NULL          (해설)
├── created_at      TIMESTAMP NOT NULL
└── updated_at      TIMESTAMP NOT NULL

USER_PROBLEM_HISTORY
├── id              BIGINT (PK, SEQ)
├── user_id         BIGINT (FK → USERS.id) NOT NULL
├── problem_id      BIGINT (FK → PROBLEMS.id) NOT NULL
├── user_answer     VARCHAR2(200) NOT NULL
├── is_correct      NUMBER(1) NOT NULL     (0: 오답, 1: 정답)
└── solved_at       TIMESTAMP NOT NULL

USER_CHAPTER_STATS
├── id              BIGINT (PK, SEQ)
├── user_id         BIGINT (FK → USERS.id) NOT NULL
├── chapter_id      BIGINT (FK → CHAPTERS.id) NOT NULL
├── correct_count   INT DEFAULT 0 NOT NULL
├── total_count     INT DEFAULT 0 NOT NULL
├── current_difficulty VARCHAR2(10) DEFAULT 'MEDIUM' NOT NULL
└── updated_at      TIMESTAMP NOT NULL

INDEX:
- USER_CHAPTER_STATS (user_id, chapter_id) UNIQUE
- USER_PROBLEM_HISTORY (user_id, solved_at DESC)
```

### 5.3 관계 요약
```
USERS 1 ─── N USER_PROBLEM_HISTORY N ─── 1 PROBLEMS
USERS 1 ─── N USER_CHAPTER_STATS   N ─── 1 CHAPTERS
CHAPTERS 1 ─── N LEARNING_CONTENTS
CHAPTERS 1 ─── N PROBLEMS
```

---

## 6. API 명세서

> Base URL: `http://localhost:8080`
> 인증: `Authorization: Bearer {accessToken}` (로그인 필요 API)

---

### 6.1 인증 API (기구현)

| Method | URL | 인증 | 설명 |
|--------|-----|------|------|
| POST | /api/auth/email/send | ✗ | 이메일 인증코드 발송 |
| POST | /api/auth/email/verify | ✗ | 이메일 인증코드 확인 |
| POST | /api/auth/signup | ✗ | 회원가입 |
| POST | /api/auth/login | ✗ | 로그인 |
| POST | /api/auth/logout | ✓ | 로그아웃 |
| POST | /api/auth/refresh | ✗ | 토큰 재발급 |
| GET | /oauth2/authorization/google | ✗ | Google OAuth2 시작 |

---

### 6.2 유저 API (신규)

#### GET /api/users/me
- **설명**: 내 프로필 조회
- **인증**: 필요
- **응답 200**:
```json
{
  "id": 1,
  "email": "user@example.com",
  "nickname": "수학왕",
  "grade": "MIDDLE_1",
  "profileImageUrl": "https://...",
  "provider": "LOCAL"
}
```

#### PATCH /api/users/me/grade
- **설명**: 학년 설정/변경
- **인증**: 필요
- **요청 Body**:
```json
{
  "grade": "MIDDLE_1"
}
```
- **응답 200**:
```json
{
  "grade": "MIDDLE_1",
  "message": "학년이 변경되었습니다."
}
```
- **검증**: grade 값은 `ELEM_3 ~ HIGH_1` 범위여야 함

#### PATCH /api/users/me/profile
- **설명**: 닉네임 수정
- **인증**: 필요
- **요청 Body**:
```json
{
  "nickname": "수학천재"
}
```

---

### 6.3 챕터 API (신규)

#### GET /api/chapters
- **설명**: 내 학년에 해당하는 챕터 목록 조회
- **인증**: 필요
- **응답 200**:
```json
[
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
  }
]
```

#### GET /api/chapters/{chapterId}
- **설명**: 챕터 상세 조회
- **인증**: 필요
- **응답 200**: 챕터 정보 + 소단원 목록

#### GET /api/chapters/{chapterId}/contents
- **설명**: 챕터별 학습 내용(소단원) 목록
- **인증**: 필요
- **응답 200**:
```json
[
  {
    "id": 1,
    "title": "분수란 무엇인가?",
    "orderNum": 1
  },
  {
    "id": 2,
    "title": "분수의 크기 비교",
    "orderNum": 2
  }
]
```

#### GET /api/chapters/{chapterId}/contents/{contentId}
- **설명**: 학습 내용 상세 (본문 포함)
- **인증**: 필요
- **응답 200**:
```json
{
  "id": 1,
  "title": "분수란 무엇인가?",
  "body": "분수는 전체를 똑같이 나눈 것 중의 몇 개를 나타냅니다...",
  "orderNum": 1
}
```

---

### 6.4 문제 API (신규)

#### GET /api/problems/adaptive
- **설명**: 정답률 기반 맞춤형 문제 조회 (1문제)
- **인증**: 필요
- **Query Params**: `chapterId={Long}`
- **동작**: USER_CHAPTER_STATS의 current_difficulty에 맞는 문제 랜덤 반환
- **응답 200**:
```json
{
  "id": 42,
  "chapterId": 1,
  "difficulty": "MEDIUM",
  "problemType": "MULTIPLE_CHOICE",
  "question": "3/4 + 1/4 = ?",
  "options": ["1/2", "1", "4/4", "2/4"],
  "currentDifficulty": "MEDIUM",
  "correctRate": 65.0
}
```

#### GET /api/problems/random
- **설명**: 랜덤 문제 조회 (난이도 무관)
- **인증**: 필요
- **Query Params**: `chapterId={Long}`
- **응답 200**: 위와 동일 구조 (difficulty는 랜덤)

#### POST /api/problems/{problemId}/submit
- **설명**: 답안 제출
- **인증**: 필요
- **요청 Body**:
```json
{
  "chapterId": 1,
  "userAnswer": "1"
}
```
- **응답 200**:
```json
{
  "isCorrect": true,
  "correctAnswer": "1",
  "explanation": "3/4 + 1/4 = 4/4 = 1 입니다. 분모가 같은 분수는...",
  "updatedDifficulty": "HIGH",
  "updatedCorrectRate": 70.0
}
```
- **동작**: USER_PROBLEM_HISTORY 저장 + USER_CHAPTER_STATS 업데이트 + 난이도 재계산

---

### 6.5 통계 API (신규)

#### GET /api/stats/me
- **설명**: 내 전체 학습 통계
- **인증**: 필요
- **응답 200**:
```json
{
  "totalSolved": 120,
  "totalCorrect": 85,
  "overallCorrectRate": 70.8,
  "chapterStats": [
    {
      "chapterId": 1,
      "chapterTitle": "분수",
      "correctCount": 8,
      "totalCount": 10,
      "correctRate": 80.0,
      "currentDifficulty": "HIGH"
    }
  ]
}
```

---

### 6.6 AI 챗봇 API (FastAPI - 기구현/개선)

> Base URL: `http://localhost:8001`
> 인증: `Authorization: Bearer {accessToken}`

#### POST /ai/chat
- **설명**: AI 챗봇 질문 (RAG 기반)
- **인증**: 필요 (Spring Boot 발급 JWT 사용)
- **요청 Body**:
```json
{
  "question": "분수의 덧셈은 어떻게 하나요?",
  "session_id": "user-123-session-abc"
}
```
- **응답 200**:
```json
{
  "answer": "분수의 덧셈을 설명해드릴게요! 분모가 같은 경우에는...",
  "session_id": "user-123-session-abc"
}
```
- **내부 동작**:
  1. JWT에서 user_id, grade 추출
  2. Supabase pgvector에서 grade 필터 + 유사도 검색
  3. 검색된 자료 + 질문 + Redis 대화이력 → Gemini 프롬프트 구성
  4. Gemini 답변 생성 → 반환 + Redis 이력 저장

#### GET /health
- **설명**: 서버 상태 확인
- **인증**: 불필요

---

### 6.7 관리자 API (신규)

> 인증 필요 + ADMIN 역할 필요

| Method | URL | 설명 |
|--------|-----|------|
| POST | /api/admin/chapters | 챕터 생성 |
| PUT | /api/admin/chapters/{id} | 챕터 수정 |
| DELETE | /api/admin/chapters/{id} | 챕터 삭제 |
| POST | /api/admin/chapters/{id}/contents | 소단원 생성 |
| PUT | /api/admin/contents/{id} | 소단원 수정 |
| DELETE | /api/admin/contents/{id} | 소단원 삭제 |
| POST | /api/admin/problems | 문제 생성 |
| PUT | /api/admin/problems/{id} | 문제 수정 |
| DELETE | /api/admin/problems/{id} | 문제 삭제 |
| POST | /api/admin/rag/upload | RAG 자료 업로드 (벡터화 포함) |

---

## 7. RAG 데이터 파이프라인

### 7.1 개요
```
AI Hub 수학 자료
      │
      ▼
[전처리] 텍스트 추출, 정제
      │
      ▼
[청킹] 의미 단위로 분할 (약 500 토큰)
      │
      ▼
[메타데이터 태깅] grade, chapter_title, topic
      │
      ▼
[임베딩] text-embedding-004 (Google)
      │
      ▼
[저장] Supabase pgvector
      │
      ▼
[검색] 질문 임베딩 → 코사인 유사도 검색 (grade 필터 포함)
      │
      ▼
[프롬프트 주입] 검색 결과 + 질문 → Gemini
```

### 7.2 Supabase 테이블 구조
```sql
CREATE TABLE math_documents (
    id          BIGSERIAL PRIMARY KEY,
    grade       VARCHAR(20) NOT NULL,    -- ELEM_3 ~ HIGH_1
    chapter     VARCHAR(100) NOT NULL,   -- 챕터명
    topic       VARCHAR(200),            -- 소주제
    content     TEXT NOT NULL,           -- 청킹된 내용
    embedding   VECTOR(768) NOT NULL,    -- text-embedding-004
    created_at  TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX ON math_documents
  USING ivfflat (embedding vector_cosine_ops);
```

### 7.3 검색 쿼리 (Python 예시)
```python
# 학년 필터 + 유사도 검색
results = supabase.rpc("match_math_documents", {
    "query_embedding": query_vector,
    "grade_filter": user_grade,
    "match_count": 5
})
```

### 7.4 업로드 파이프라인 스크립트 위치
- `ai-server/scripts/upload_rag_data.py` (신규 작성 예정)

---

## 8. 난이도 조정 알고리즘

### 8.1 기준
- **기준 문제 수**: 챕터별 최근 **10문제** (또는 전체 누적)
- **초기 난이도**: MEDIUM

### 8.2 조정 로직
```
정답률 = (correct_count / total_count) * 100

if total_count < 3:
    → 난이도 변경 없음 (데이터 부족)
elif 정답률 >= 80:
    → 난이도 상향: LOW → MEDIUM → HIGH
elif 정답률 < 50:
    → 난이도 하향: HIGH → MEDIUM → LOW
else (50 <= 정답률 < 80):
    → 현재 난이도 유지
```

### 8.3 적용 시점
- 답안 제출 (`POST /api/problems/{id}/submit`) 때마다 재계산
- USER_CHAPTER_STATS.current_difficulty 업데이트
- 다음 문제 요청 시 새 난이도 적용

### 8.4 예시 시나리오
```
[초기] difficulty = MEDIUM
문제 1: 정답 → 1/1 = 100% → 유지 (3문제 미만)
문제 2: 정답 → 2/2 = 100% → 유지 (3문제 미만)
문제 3: 정답 → 3/3 = 100% → HIGH로 상향 ✓
...
문제 8: 오답 → 6/8 = 75% → HIGH 유지
문제 9: 오답 → 6/9 = 66% → HIGH 유지
문제 10: 오답 → 6/10 = 60% → HIGH 유지
문제 11: 오답 → 6/11 = 54% → HIGH 유지
문제 12: 오답 → 6/12 = 50% → HIGH 유지
문제 13: 오답 → 6/13 = 46% → MEDIUM으로 하향 ✓
```

---

## 9. 개발 마일스톤

### Phase 1: 기반 완성 (1~2주)
- [x] Spring Boot 인증 구현 (JWT, OAuth2, 이메일)
- [x] FastAPI 기본 구조 (JWT 공유, Redis 대화메모리)
- [ ] **User 엔티티 grade 컬럼 추가** (Oracle DDL + JPA 수정)
- [ ] **학년 설정/조회 API** 구현
- [ ] Oracle ailang 유저 확인 + Spring Boot 정상 실행 확인

### Phase 2: 핵심 기능 구현 (2~3주)
- [ ] CHAPTERS, LEARNING_CONTENTS, PROBLEMS 테이블 생성
- [ ] 챕터/학습내용 CRUD API
- [ ] 문제 풀기 API (랜덤, 맞춤형)
- [ ] 답안 제출 + 난이도 조정 로직
- [ ] 통계 API

### Phase 3: AI/RAG 완성 (1~2주)
- [ ] AI Hub 수학 자료 수집 + 전처리
- [ ] Supabase pgvector 테이블 생성
- [ ] RAG 업로드 파이프라인 스크립트 작성
- [ ] FastAPI rag_service.py RAG 검색 구현 (현재 TODO)
- [ ] 학년 컨텍스트 프롬프트 주입

### Phase 4: 관리자 기능 + 마무리 (1주)
- [ ] 관리자 API (챕터/문제/소단원 관리)
- [ ] 전체 API 통합 테스트
- [ ] Spring Boot ↔ FastAPI 연동 확인

---

## 부록: Grade Enum 정의

```java
// Spring Boot (Java)
public enum Grade {
    ELEM_3("초등 3학년"),
    ELEM_4("초등 4학년"),
    ELEM_5("초등 5학년"),
    ELEM_6("초등 6학년"),
    MIDDLE_1("중학 1학년"),
    MIDDLE_2("중학 2학년"),
    MIDDLE_3("중학 3학년"),
    HIGH_1("고등 1학년");
}
```

```python
# FastAPI (Python)
from enum import Enum

class Grade(str, Enum):
    ELEM_3 = "ELEM_3"
    ELEM_4 = "ELEM_4"
    ELEM_5 = "ELEM_5"
    ELEM_6 = "ELEM_6"
    MIDDLE_1 = "MIDDLE_1"
    MIDDLE_2 = "MIDDLE_2"
    MIDDLE_3 = "MIDDLE_3"
    HIGH_1 = "HIGH_1"
```
