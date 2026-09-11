package com.example.ailang.global.loader;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 대단원 이름표.
 *
 * <p>🔴 <b>왜 표가 필요한가 — 원본 자료에 단원 «이름» 이 없기 때문이다.</b>
 *
 * <p>원본 JSON 의 계층은 <b>학기 → 단원 → 유형</b> 3층뿐이다
 * ({@code question_topic} 코드 7자리 = 학년1 + 학기1 + 단원2 + 유형일련번호3).
 * 단원에는 번호({@code question_unit} = {@code "01"}~{@code "08"})만 있고 이름이 없다.
 * 그래서 챕터 제목을 이 표에서 가져온다.
 *
 * <p>⚠️ <b>없으면 만들어 내지 않는다.</b> 표에 없는 단원은 제목을
 * {@code "(이름 미등록) 03단원"} 으로 두고 그 건수를 적재 로그에 남긴다.
 * 그럴듯한 이름을 지어내면 학생 화면에 <b>틀린 단원명</b>이 그대로 뜬다.
 *
 * <p>근거: {@code docs/research/chapter-granularity-2026-09-09.md}
 */
@Slf4j
public final class ChapterTitles {

    private static final String PATH = "data/chapter-titles.csv";

    private final Map<String, String> byKey;

    private ChapterTitles(Map<String, String> byKey) {
        this.byKey = byKey;
    }

    /** 클래스패스에서 읽는다. 파일이 없거나 깨져도 적재를 막지 않는다. */
    public static ChapterTitles load() {
        Map<String, String> map = new HashMap<>();
        ClassPathResource resource = new ClassPathResource(PATH);

        if (!resource.exists()) {
            log.warn("[DataLoader] 단원 이름표가 없습니다 ({}). 챕터 제목이 전부 "
                    + "「(이름 미등록)」 으로 들어갑니다.", PATH);
            return new ChapterTitles(map);
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
                if (comma <= 0 || comma == trimmed.length() - 1) {
                    bad++;
                    continue;
                }
                map.put(trimmed.substring(0, comma).trim(),
                        trimmed.substring(comma + 1).trim());
            }
        } catch (Exception e) {
            log.warn("[DataLoader] 단원 이름표를 읽지 못했습니다: {}", e.toString());
            return new ChapterTitles(Map.of());
        }

        if (bad > 0) {
            log.warn("[DataLoader] 단원 이름표에서 읽지 못한 줄 {}건", bad);
        }
        log.info("[DataLoader] 단원 이름표 {}건 로딩", map.size());
        return new ChapterTitles(map);
    }

    /** {@code "M1-03"} 처럼 「학년코드-단원번호」로 찾는다. */
    public static String key(String gradeCode, String unit) {
        return gradeCode + "-" + unit;
    }

    /** 등록된 이름이 있으면 그 값, 없으면 {@code null}. */
    public String get(String key) {
        return key == null ? null : byKey.get(key);
    }

    public int size() {
        return byKey.size();
    }
}
