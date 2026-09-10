package com.example.ailang.global.loader;

import com.example.ailang.domain.problem.enums.Difficulty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 문제별 난이도 스냅샷.
 *
 * <p>🔴 <b>왜 표를 들고 있나 — 난이도를 원본 자료에서 유도할 수 없기 때문이다.</b>
 *
 * <p>지금 DB 에는 하454·중432·상246 이 들어 있는데, 2026-09-07 에 원본 JSON 의 모든 필드를
 * 대조해 봤더니 <b>어느 것도 이 값을 설명하지 못했다.</b>
 * <pre>
 *   단일 필드 최고    question_unit  86.4%  (값 8종)
 *   두 필드 조합 최고               93.2%  ← 조합이 504종이라 규칙이 아니라 과적합
 *   진도(topic 코드) 순서            ❌ unit 03 에서 역전 290/2485 쌍
 *   question_difficulty 1/2/3      33.7%  ← 3문제 중 2개가 달라진다
 * </pre>
 * 단원 안에서 난이도가 갈리는 것을 <b>어떤 필드도 구분하지 못한다</b> — 설명력이 전부
 * 다수결 기준선과 정확히 같다(= 정보가 0). 즉 <b>사람이 손으로 정한 값</b>이다.
 *
 * <p>🎯 유도할 수 없다고 재현까지 못 하는 것은 아니다. <b>기록해 두면 된다.</b>
 * 그래서 「원본 id → 난이도」를 파일로 남기고 적재할 때 그대로 쓴다. DB 를 비워도
 * 같은 결과가 나온다.
 *
 * <p>⚠️ 이 표는 <b>규칙이 아니라 기록</b>이다. 새 학년 자료에는 해당 id 가 없으므로
 * {@code DataLoader} 의 기존 매핑으로 떨어지고, 그 건수는 적재 로그에 남는다.
 * 난이도 축을 «무엇으로 정할 것인가» 는 여전히 결정 대기 항목이다
 * ({@code TODOS.md} 0절 · 8절).
 *
 * <p>근거: {@code docs/research/difficulty-origin-2026-09-07.md}
 */
@Slf4j
public final class DifficultyOverrides {

    private static final String PATH = "data/difficulty-overrides.csv";

    private final Map<String, Difficulty> byId;

    private DifficultyOverrides(Map<String, Difficulty> byId) {
        this.byId = byId;
    }

    /** 클래스패스에서 읽는다. 파일이 없거나 깨져도 적재를 막지 않는다. */
    public static DifficultyOverrides load() {
        Map<String, Difficulty> map = new HashMap<>();
        ClassPathResource resource = new ClassPathResource(PATH);

        if (!resource.exists()) {
            // 🔴 없다고 터뜨리지 않는다. 다만 «있는 줄 알았는데 없었다» 를 알 수 있게 남긴다.
            log.warn("[DataLoader] 난이도 스냅샷이 없습니다 ({}). 원본 필드로만 정합니다 "
                    + "— 상(HIGH) 이 한 건도 안 나올 수 있습니다.", PATH);
            return new DifficultyOverrides(map);
        }

        int bad = 0;
        try (InputStream in = resource.getInputStream();
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    continue;
                }
                int comma = trimmed.indexOf(',');
                if (comma <= 0) {
                    bad++;
                    continue;
                }
                String id = trimmed.substring(0, comma).trim();
                String value = trimmed.substring(comma + 1).trim();
                try {
                    map.put(id, Difficulty.valueOf(value));
                } catch (IllegalArgumentException e) {
                    bad++;   // 어휘 밖의 값은 조용히 삼키지 않는다
                }
            }
        } catch (Exception e) {
            log.warn("[DataLoader] 난이도 스냅샷을 읽지 못했습니다: {}", e.toString());
            return new DifficultyOverrides(Map.of());
        }

        if (bad > 0) {
            log.warn("[DataLoader] 난이도 스냅샷에서 읽지 못한 줄 {}건", bad);
        }
        log.info("[DataLoader] 난이도 스냅샷 {}건 로딩", map.size());
        return new DifficultyOverrides(map);
    }

    /** 기록이 있으면 그 값, 없으면 {@code null}. */
    public Difficulty get(String aihubId) {
        return aihubId == null ? null : byId.get(aihubId);
    }

    public int size() {
        return byId.size();
    }
}
