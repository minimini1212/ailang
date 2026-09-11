#!/usr/bin/env python3
"""
객관식 정답 정규화 스크립트 (1회성 실행)
- MULTIPLE_CHOICE 문제의 answer 컬럼에서 원문자(①②③④⑤)만 추출해 숫자로 변환
- 예: "① 어떤 수를 a라고..." → "1"
- 예: "①, ③"               → "1,3"
- 예: "(해답)②"             → "2"
- 예: "$ ④ $"              → "4"

사용법:
  pip install oracledb
  python scripts/normalize_answers.py
"""

import re
import oracledb

DB_USER     = "ailang"
DB_PASSWORD = "ailang"
DB_DSN      = "localhost:1521/FREEPDB1"

# 원문자 → 숫자 매핑
CIRCLE_MAP = {'①': '1', '②': '2', '③': '3', '④': '4', '⑤': '5'}


def normalize_mc_answer(answer: str) -> str | None:
    """
    객관식 정답 문자열에서 원문자(①~⑤)만 순서대로 추출해 "1", "1,3" 형태로 반환
    원문자가 없으면 괄호형 숫자 (1)~(5) 시도
    둘 다 없으면 None 반환
    """
    if not answer:
        return None

    # ①②③④⑤ 추출 (등장 순서 유지, 중복 제거)
    found = []
    seen = set()
    for ch in answer:
        if ch in CIRCLE_MAP and CIRCLE_MAP[ch] not in seen:
            found.append(CIRCLE_MAP[ch])
            seen.add(CIRCLE_MAP[ch])

    # 원문자 없으면 괄호형 시도: (1), (2), (3), (4), (5)
    if not found:
        matches = re.findall(r'\(([1-5])\)', answer)
        for m in matches:
            if m not in seen:
                found.append(m)
                seen.add(m)

    return ','.join(found) if found else None


def main():
    print("=" * 50)
    print("객관식 정답 정규화 스크립트")
    print("=" * 50)

    conn   = oracledb.connect(user=DB_USER, password=DB_PASSWORD, dsn=DB_DSN)
    cursor = conn.cursor()

    cursor.execute(
        "SELECT ID, ANSWER FROM PROBLEMS WHERE PROBLEM_TYPE = 'MULTIPLE_CHOICE'"
    )
    rows = cursor.fetchall()
    print(f"\n객관식 문제 {len(rows)}개 처리 중...\n")

    updated = 0
    failed  = []

    for problem_id, answer in rows:
        normalized = normalize_mc_answer(answer or '')

        if normalized:
            cursor.execute(
                "UPDATE PROBLEMS SET ANSWER = :answer WHERE ID = :id",
                answer=normalized, id=problem_id
            )
            updated += 1
        else:
            failed.append((problem_id, answer))

    conn.commit()
    cursor.close()
    conn.close()

    print(f"완료: 업데이트 {updated}개")

    if failed:
        print(f"\n정규화 실패 {len(failed)}개 (정답 추출 불가):")
        for pid, ans in failed[:10]:
            preview = (ans or '')[:60].replace('\n', ' ')
            print(f"  id={pid}: {repr(preview)}")
        if len(failed) > 10:
            print(f"  ... 외 {len(failed) - 10}개")
    print("=" * 50)


if __name__ == "__main__":
    main()
