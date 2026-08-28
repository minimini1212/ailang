package com.example.ailang.global.loader;

import com.example.ailang.domain.chapter.entity.Chapter;
import com.example.ailang.domain.chapter.repository.ChapterRepository;
import com.example.ailang.domain.problem.entity.Problem;
import com.example.ailang.domain.problem.enums.Difficulty;
import com.example.ailang.domain.problem.enums.ProblemType;
import com.example.ailang.domain.problem.enums.SourceType;
import com.example.ailang.domain.problem.repository.ProblemRepository;
import com.example.ailang.domain.user.enums.Grade;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.*;

/**
 * 앱 시작 시 기출문제 JSON 파일을 읽어 DB에 자동 적재
 * - PROBLEMS 테이블이 비어 있을 때만 실행 (중복 적재 방지)
 * - application.yml의 data.loader.enabled=false 로 비활성화 가능
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {

    private final ProblemRepository problemRepository;
    private final ChapterRepository chapterRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${data.loader.enabled:true}")
    private boolean enabled;

    @Value("${data.loader.problem-dir}")
    private String problemDir;

    @Value("${data.loader.answer-dir}")
    private String answerDir;

    // 학년 코드 → Grade enum 매핑
    private static final Map<String, Grade> GRADE_MAP = Map.of(
            "E3", Grade.ELEM_3,   "E4", Grade.ELEM_4,
            "E5", Grade.ELEM_5,   "E6", Grade.ELEM_6,
            "M1", Grade.MIDDLE_1, "M2", Grade.MIDDLE_2, "M3", Grade.MIDDLE_3,
            "H1", Grade.HIGH_1
    );

    // question_step → Difficulty 매핑
    private static final Map<String, Difficulty> STEP_MAP = Map.of(
            "기본", Difficulty.LOW,
            "표준", Difficulty.MEDIUM,
            "심화", Difficulty.HIGH
    );

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("[DataLoader] 비활성화 상태 (data.loader.enabled=false)");
            return;
        }
        if (problemRepository.count() > 0) {
            log.info("[DataLoader] 문제 데이터가 이미 존재합니다. 적재를 건너뜁니다.");
            return;
        }

        // 「경로를 설정 안 했다」와 「경로가 틀렸다」는 다른 문제이고, 사람이 할 일도 다르다.
        // 둘을 같은 메시지로 뭉뚱그리면 원인을 못 찾는다 (docs/rules 의 「실패는 종류를 남긴다」).
        if (problemDir == null || problemDir.isBlank() || answerDir == null || answerDir.isBlank()) {
            log.warn("[DataLoader] 적재 경로가 설정되지 않았습니다. 문제 0건으로 기동합니다.");
            log.warn("[DataLoader] .env 에 DATA_PROBLEM_DIR · DATA_ANSWER_DIR 을 채우세요 (.env.example ⑦ 참고).");
            return;
        }

        log.info("[DataLoader] 기출문제 적재 시작...");
        log.info("[DataLoader] 문제 경로: {}", problemDir);
        log.info("[DataLoader] 답안 경로: {}", answerDir);

        // JSON 파일 로딩 (id → 데이터 Map)
        Map<String, ProblemJson> problems = loadJsonDir(problemDir, ProblemJson.class);
        Map<String, AnswerJson>  answers  = loadJsonDir(answerDir,  AnswerJson.class);
        log.info("[DataLoader] 문제 {}개, 답안 {}개 로딩 완료", problems.size(), answers.size());

        // 챕터 캐시 (앱 실행 중 DB 중복 조회 방지)
        Map<String, Chapter> chapterCache = new HashMap<>();
        int inserted = 0, skipped = 0;

        for (Map.Entry<String, ProblemJson> entry : problems.entrySet()) {
            String      id   = entry.getKey();
            ProblemJson prob = entry.getValue();
            AnswerJson  ans  = answers.get(id);

            if (ans == null || ans.answerInfo == null || ans.answerInfo.isEmpty()) {
                skipped++;
                continue;
            }

            try {
                ProblemJson.QuestionInfo info       = prob.questionInfo.get(0);
                ProblemJson.OcrInfo      ocr        = prob.ocrInfo.get(0);
                AnswerJson.AnswerInfo    answerInfo = ans.answerInfo.get(0);

                // 학년 변환
                Grade grade = GRADE_MAP.get(info.questionGrade);
                if (grade == null) {
                    log.warn("[DataLoader] {} - 알 수 없는 학년 코드: {}", id, info.questionGrade);
                    skipped++;
                    continue;
                }

                String questionText = ocr.questionText;
                String explanation  = answerInfo.answerText;
                String answer       = extractAnswer(answerInfo.answerBbox);

                if (answer == null || answer.isBlank()) {
                    log.warn("[DataLoader] {} - 정답 추출 실패", id);
                    skipped++;
                    continue;
                }

                // 챕터 조회 또는 생성
                Chapter chapter = getOrCreateChapter(chapterCache, grade, info);

                // 난이도 변환 (question_step 우선, 없으면 question_difficulty 점수 기반)
                Difficulty difficulty = STEP_MAP.getOrDefault(
                        info.questionStep, difficultyFromScore(info.questionDifficulty));

                // 문제 유형 변환
                ProblemType type = "선택형".equals(info.questionType1)
                        ? ProblemType.MULTIPLE_CHOICE : ProblemType.SHORT_ANSWER;

                problemRepository.save(Problem.builder()
                        .chapter(chapter)
                        .difficulty(difficulty)
                        .problemType(type)
                        .question(questionText)
                        .options(null)   // 보기는 question_text 안에 포함되어 있음
                        .answer(answer)
                        .explanation(explanation)
                        .sourceType(SourceType.REAL)
                        .build());

                inserted++;
                if (inserted % 100 == 0) {
                    log.info("[DataLoader] {}개 삽입 완료...", inserted);
                }

            } catch (Exception e) {
                log.warn("[DataLoader] {} 처리 실패: {}", id, e.getMessage());
                skipped++;
            }
        }

        log.info("[DataLoader] 적재 완료 - 삽입: {}개, 건너뜀: {}개", inserted, skipped);
    }

    // ─── 헬퍼 메서드 ────────────────────────────────────────────

    /** 디렉토리 내 모든 JSON 파일을 id 기준 Map으로 로딩 */
    private <T> Map<String, T> loadJsonDir(String dirPath, Class<T> clazz) {
        Map<String, T> result = new LinkedHashMap<>();
        File dir = new File(dirPath);

        if (!dir.exists() || !dir.isDirectory()) {
            log.error("[DataLoader] 디렉토리를 찾을 수 없습니다: {}", dirPath);
            return result;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return result;

        for (File file : files) {
            try {
                T data = objectMapper.readValue(file, clazz);
                // id 추출 (raw Map으로 한 번 더 읽어 id 값 확인)
                @SuppressWarnings("unchecked")
                Map<String, Object> raw = objectMapper.readValue(file, Map.class);
                String id = (String) raw.get("id");
                if (id != null) result.put(id, data);
            } catch (Exception e) {
                log.warn("[DataLoader] 파일 읽기 실패 {}: {}", file.getName(), e.getMessage());
            }
        }
        return result;
    }

    /** answer_bbox 에서 type='answer' 항목의 텍스트 추출 */
    private String extractAnswer(List<AnswerJson.BboxItem> bboxList) {
        if (bboxList == null) return null;
        return bboxList.stream()
                .filter(b -> "answer".equals(b.type))
                .map(b -> b.text)
                .filter(t -> t != null && !t.isBlank())
                .findFirst()
                .orElse(null);
    }

    /** 챕터 캐시에서 조회, 없으면 DB 조회 후 없으면 생성 */
    private Chapter getOrCreateChapter(Map<String, Chapter> cache,
                                       Grade grade,
                                       ProblemJson.QuestionInfo info) {
        String key = grade.name() + "_" + info.questionTopicName;
        return cache.computeIfAbsent(key, k ->
                chapterRepository.findByGradeAndTitle(grade, info.questionTopicName)
                        .orElseGet(() -> {
                            Chapter newChapter = chapterRepository.save(Chapter.builder()
                                    .grade(grade)
                                    .title(info.questionTopicName)
                                    .orderNum(Integer.parseInt(info.questionUnit))
                                    .build());
                            log.info("[DataLoader] 챕터 생성: {} - {}", grade, info.questionTopicName);
                            return newChapter;
                        })
        );
    }

    /** question_difficulty(1~5) 점수 → Difficulty 변환 */
    private Difficulty difficultyFromScore(int score) {
        if (score <= 2) return Difficulty.LOW;
        if (score <= 3) return Difficulty.MEDIUM;
        return Difficulty.HIGH;
    }

    // ─── JSON 역직렬화용 내부 클래스 ────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ProblemJson {
        public String id;

        @JsonProperty("question_info")
        public List<QuestionInfo> questionInfo;

        @JsonProperty("OCR_info")
        public List<OcrInfo> ocrInfo;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class QuestionInfo {
            @JsonProperty("question_grade")      public String questionGrade;
            @JsonProperty("question_unit")       public String questionUnit;
            @JsonProperty("question_topic_name") public String questionTopicName;
            @JsonProperty("question_type1")      public String questionType1;
            @JsonProperty("question_step")       public String questionStep;
            @JsonProperty("question_difficulty") public int    questionDifficulty;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class OcrInfo {
            @JsonProperty("question_text")
            public String questionText;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnswerJson {
        public String id;

        @JsonProperty("answer_info")
        public List<AnswerInfo> answerInfo;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class AnswerInfo {
            @JsonProperty("answer_text")
            public String answerText;

            @JsonProperty("answer_bbox")
            public List<BboxItem> answerBbox;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class BboxItem {
            public String type;
            public String text;
        }
    }
}
