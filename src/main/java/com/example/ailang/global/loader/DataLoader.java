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
    //
    // ⚠️ HashMap 이다. Map.of 로 만든 불변 맵은 **null 키를 조회하는 것만으로 NPE** 다
    //    (ImmutableCollections.MapN 이 pk.hashCode() 를 부른다). 그래서 예전에는
    //    JSON 에 question_grade 가 없으면 바로 아래의 「알 수 없는 학년 코드」 가드에
    //    도달하지 못하고 바깥 catch 로 떨어져 «처리 실패: null» 한 줄만 남았다.
    //    📌 실측(2026-08-31)으로는 이 자료에 누락이 0건이라 지금 터지지는 않는다.
    //       그래도 가드가 도달 가능해야 가드다.
    private static final Map<String, Grade> GRADE_MAP = new HashMap<>(Map.of(
            "E3", Grade.ELEM_3,   "E4", Grade.ELEM_4,
            "E5", Grade.ELEM_5,   "E6", Grade.ELEM_6,
            "M1", Grade.MIDDLE_1, "M2", Grade.MIDDLE_2, "M3", Grade.MIDDLE_3,
            "H1", Grade.HIGH_1
    ));

    // question_step → Difficulty 매핑
    //
    // 🔴 실제 자료에 있는 값은 「기본」(935건)과 「실생활응용」(217건) 둘뿐이다.
    //    「표준」·「심화」는 **한 건도 없어서** 아래 두 줄은 한 번도 안 쓰인다.
    //    그래서 이 매핑만으로는 상(HIGH) 이 나오지 않는다 — 난이도 축을 어떻게 정할지는
    //    사용자 결정 대기 항목이다 (TODOS.md 0절·8절).
    //    ⚠️ 여기서 매핑을 바꾸지 말 것. 결정 전에 바꾸면 지금 DB 와 또 어긋난다.
    private static final Map<String, Difficulty> STEP_MAP = new HashMap<>(Map.of(
            "기본", Difficulty.LOW,
            "표준", Difficulty.MEDIUM,
            "심화", Difficulty.HIGH
    ));

    @Override
    public void run(String... args) {
        if (!enabled) {
            log.info("[DataLoader] 비활성화 상태 (data.loader.enabled=false)");
            return;
        }
        // 🔴 «기출문제» 가 있는지를 본다. 전체 건수가 아니다.
        //    예전에는 problemRepository.count() 였는데, AI 모의문제도 같은 표에 저장된다.
        //    그래서 적재 경로가 틀려 0건으로 뜬 상태에서 학생이 AI 문제를 **한 번만**
        //    만들면 count 가 1이 되어, 그 뒤로는 기출 적재를 **영원히 건너뛴다.**
        //    로그는 「이미 존재합니다」라고 안심시킨다.
        long realCount = problemRepository.countBySourceType(SourceType.REAL);
        if (realCount > 0) {
            log.info("[DataLoader] 기출문제 {}건이 이미 있습니다. 적재를 건너뜁니다.", realCount);
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

        // 챕터 캐시 (앱 실행 중 DB 중복 조회 방지). 키는 「학년코드-단원번호」다.
        Map<String, Chapter> chapterCache = new HashMap<>();

        // 🔴 대단원에는 원본에 «이름» 이 없다. 표에서 가져오고, 없으면 지어내지 않는다.
        //    (근거: ChapterTitles 주석 · docs/research/chapter-granularity-2026-09-09.md)
        ChapterTitles titles = ChapterTitles.load();
        int unnamedChapters = 0;

        // 🔴 실패를 사유별로 센다. 예전에는 전부 skipped 하나였고, 그러면
        //    「몇 건 안 들어왔다」는 알아도 무엇을 고쳐야 하는지는 알 수 없었다.
        EnumMap<LoadOutcome, Integer> tally = new EnumMap<>(LoadOutcome.class);
        // 난이도를 점수로 떨어뜨린 건수 — STEP_MAP 에 없는 값이 얼마나 되는지 보이게 한다
        int steppedByScore = 0;
        int multiAnswer = 0;

        // 🔴 난이도는 원본에서 유도되지 않는다. 기록해 둔 스냅샷을 먼저 본다.
        //    (근거: DifficultyOverrides 주석 · docs/research/difficulty-origin-2026-09-07.md)
        DifficultyOverrides overrides = DifficultyOverrides.load();
        int fromSnapshot = 0;

        for (Map.Entry<String, ProblemJson> entry : problems.entrySet()) {
            String      id   = entry.getKey();
            ProblemJson prob = entry.getValue();
            AnswerJson  ans  = answers.get(id);

            if (ans == null || ans.answerInfo == null || ans.answerInfo.isEmpty()) {
                count(tally, LoadOutcome.NO_ANSWER_FILE);
                continue;
            }

            try {
                if (prob.questionInfo == null || prob.questionInfo.isEmpty()
                        || prob.ocrInfo == null || prob.ocrInfo.isEmpty()) {
                    log.warn("[DataLoader] {} - question_info 또는 OCR_info 가 없습니다", id);
                    count(tally, LoadOutcome.MISSING_FIELD);
                    continue;
                }

                ProblemJson.QuestionInfo info       = prob.questionInfo.get(0);
                ProblemJson.OcrInfo      ocr        = prob.ocrInfo.get(0);
                AnswerJson.AnswerInfo    answerInfo = ans.answerInfo.get(0);

                // 학년 변환
                if (info.questionGrade == null) {
                    log.warn("[DataLoader] {} - question_grade 필드가 없습니다", id);
                    count(tally, LoadOutcome.MISSING_FIELD);
                    continue;
                }
                Grade grade = GRADE_MAP.get(info.questionGrade);
                if (grade == null) {
                    log.warn("[DataLoader] {} - 처음 보는 학년 코드: {}", id, info.questionGrade);
                    count(tally, LoadOutcome.UNKNOWN_GRADE);
                    continue;
                }

                String questionText = ocr.questionText;
                String explanation  = answerInfo.answerText;

                // 🔴 정답이 여러 개인 문제가 있다 (실측 6건). 예전에는 «첫 건만» 썼는데,
                //    그러면 「①,③」이 정답인 문제가 「①」로 저장돼 학생이 맞혀도 오답이 된다.
                List<String> answers2 = extractAnswers(answerInfo.answerBbox);
                if (answers2.isEmpty()) {
                    log.warn("[DataLoader] {} - 정답 자리가 비어 있습니다", id);
                    count(tally, LoadOutcome.NO_ANSWER_TEXT);
                    continue;
                }
                if (answers2.size() > 1) {
                    multiAnswer++;
                }
                String answer = String.join(",", answers2);

                // 챕터(대단원) 조회 또는 생성
                int before = chapterCache.size();
                Chapter chapter = getOrCreateChapter(chapterCache, grade, info, titles);
                if (chapterCache.size() > before && chapter.getTitle().startsWith(UNNAMED_PREFIX)) {
                    unnamedChapters++;
                }

                // 난이도 결정 — ① 기록해 둔 스냅샷 ② 없으면 원본 필드로 유도
                //
                // 🔴 ①이 먼저인 이유: 원본 자료로는 이 난이도를 만들 수 없다.
                //    2026-09-07 에 모든 필드를 대조했고, 최고 설명력이 86.4%(question_unit)
                //    였으며 단원 안의 분화는 어떤 필드로도 설명되지 않았다.
                //    ②만 쓰면 상(HIGH) 이 **한 건도 안 나온다** — 진단 테스트가 깨진다.
                Difficulty difficulty = overrides.get(id);
                if (difficulty != null) {
                    fromSnapshot++;
                } else {
                    difficulty = STEP_MAP.get(info.questionStep);
                    if (difficulty == null) {
                        difficulty = difficultyFromScore(info.questionDifficulty);
                        steppedByScore++;
                    }
                }

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
                        // 🔴 챕터가 대단원으로 넓어진 만큼 «유형» 을 여기 남긴다.
                        //    안 남기면 331종의 정보가 통째로 사라진다.
                        .topic(info.questionTopicName)
                        // 🔴 원본 id. 이게 없어서 적재가 «전부 아니면 전무» 였다.
                        .sourceId(id)
                        .build());

                count(tally, LoadOutcome.INSERTED);
                int inserted = tally.getOrDefault(LoadOutcome.INSERTED, 0);
                if (inserted % 100 == 0) {
                    log.info("[DataLoader] {}개 삽입 완료...", inserted);
                }

            } catch (Exception e) {
                log.warn("[DataLoader] {} 처리 실패: {}", id, e.toString());
                count(tally, LoadOutcome.ERROR);
            }
        }

        report(tally, problems.size(), chapterCache.size(), steppedByScore, multiAnswer,
                fromSnapshot, overrides.size(), unnamedChapters, titles.size());
    }

    private static void count(EnumMap<LoadOutcome, Integer> tally, LoadOutcome outcome) {
        tally.merge(outcome, 1, Integer::sum);
    }

    /** 무엇이 들어왔고 무엇이 왜 빠졌는지 한눈에 남긴다. */
    private void report(EnumMap<LoadOutcome, Integer> tally, int total, int chapters,
                        int steppedByScore, int multiAnswer,
                        int fromSnapshot, int snapshotSize,
                        int unnamedChapters, int titleTableSize) {
        int inserted = tally.getOrDefault(LoadOutcome.INSERTED, 0);
        log.info("[DataLoader] ── 적재 결과 ──────────────────────────");
        log.info("[DataLoader] 대상 {}건 중 {}건 저장 · 챕터 {}개", total, inserted, chapters);
        for (LoadOutcome o : LoadOutcome.values()) {
            if (o == LoadOutcome.INSERTED) {
                continue;
            }
            int n = tally.getOrDefault(o, 0);
            if (n > 0) {
                log.warn("[DataLoader]   빠짐 {}건 - {}", n, o.label());
            }
        }
        if (multiAnswer > 0) {
            log.info("[DataLoader]   정답이 여러 개인 문제 {}건 (쉼표로 이어 저장)", multiAnswer);
        }
        // 난이도가 어디서 왔는지 — 🎯 이게 안 보이면 「왜 상(HIGH) 이 0건이지」를 못 찾는다
        log.info("[DataLoader]   난이도: 기록된 스냅샷 {}건 / 원본 필드로 유도 {}건",
                fromSnapshot, inserted - fromSnapshot);
        if (inserted > fromSnapshot) {
            int derived = inserted - fromSnapshot;
            log.warn("[DataLoader]   ⚠️ 스냅샷에 없는 문제 {}건은 원본 필드로 정했습니다. "
                    + "이 자료에는 「표준」·「심화」가 없어 상(HIGH) 이 안 나옵니다.", derived);
        }
        if (snapshotSize > fromSnapshot) {
            log.info("[DataLoader]   스냅샷 {}건 중 {}건만 쓰였습니다 (나머지는 이 자료에 없는 문제)",
                    snapshotSize, fromSnapshot);
        }
        // 챕터 이름을 못 찾은 단원 — 🎯 이게 안 보이면 「(이름 미등록)」이 학생 화면에 뜬다
        if (unnamedChapters > 0) {
            log.warn("[DataLoader]   🔴 이름을 못 찾은 대단원 {}개 (이름표 {}건). "
                    + "data/chapter-titles.csv 를 채우세요.", unnamedChapters, titleTableSize);
        }
        if (steppedByScore > 0) {
            // ⚠️ 이 숫자가 크면 question_step 매핑이 실제 자료와 안 맞는다는 뜻이다.
            log.warn("[DataLoader]   난이도를 점수로 정한 문제 {}건 "
                    + "(question_step 이 매핑에 없는 값)", steppedByScore);
        }
        if (inserted == 0) {
            log.error("[DataLoader] 🔴 한 건도 저장되지 않았습니다. 위 사유를 확인하세요.");
        }
        log.info("[DataLoader] ───────────────────────────────────────");
    }

    // ─── 헬퍼 메서드 ────────────────────────────────────────────

    /**
     * 디렉토리 내 모든 JSON 파일을 id 기준 Map 으로 로딩.
     *
     * <p>⚠️ <b>파일 이름 순으로 읽는다.</b> {@code listFiles()} 의 순서는 OS 에 따라 다른데,
     * 챕터의 {@code orderNum} 이 「먼저 만난 파일」의 값으로 정해지기 때문에 순서가 결과를
     * 바꾼다. 정렬해 두면 같은 자료로 어디서 적재해도 같은 결과가 나온다.
     */
    private <T extends HasId> Map<String, T> loadJsonDir(String dirPath, Class<T> clazz) {
        Map<String, T> result = new LinkedHashMap<>();
        File dir = new File(dirPath);

        if (!dir.exists() || !dir.isDirectory()) {
            log.error("[DataLoader] 디렉토리를 찾을 수 없습니다: {}", dirPath);
            return result;
        }

        File[] files = dir.listFiles((d, name) -> name.endsWith(".json"));
        if (files == null) return result;
        Arrays.sort(files, Comparator.comparing(File::getName));

        int unreadable = 0;
        int noId = 0;
        for (File file : files) {
            try {
                // 🎯 파일을 한 번만 읽는다. 예전에는 id 를 꺼내려고 Map 으로 한 번 더 읽었는데,
                //    id 필드는 이미 DTO 에 있고 Jackson 이 채워 준다. 1,152건이면 2,304번 파싱이었다.
                T data = objectMapper.readValue(file, clazz);
                String id = data.id();
                if (id == null) {
                    noId++;
                    continue;
                }
                if (result.putIfAbsent(id, data) != null) {
                    log.warn("[DataLoader] id 중복 - 뒤에 온 파일을 버립니다: {} ({})", id, file.getName());
                }
            } catch (Exception e) {
                unreadable++;
                log.warn("[DataLoader] 파일 읽기 실패 {}: {}", file.getName(), e.getMessage());
            }
        }
        if (unreadable > 0 || noId > 0) {
            log.warn("[DataLoader] {} - 읽기 실패 {}건 · id 없음 {}건", dirPath, unreadable, noId);
        }
        return result;
    }

    /** id 를 가진 적재 대상. 파일을 두 번 읽지 않기 위한 최소 계약이다. */
    interface HasId {
        String id();
    }

    /**
     * {@code answer_bbox} 에서 정답으로 표시된 텍스트를 <b>전부</b> 뽑는다.
     *
     * <p>🔴 예전에는 {@code findFirst()} 로 첫 건만 썼다. 정답이 여러 개인 문제가
     * 실제로 6건 있는데(실측 2026-08-31), 그러면 「①,③」이 정답인 문제가 「①」로 저장돼
     * <b>학생이 맞혀도 오답</b>이 된다.
     */
    private List<String> extractAnswers(List<AnswerJson.BboxItem> bboxList) {
        if (bboxList == null) {
            return List.of();
        }
        return bboxList.stream()
                .filter(b -> "answer".equals(b.type))
                .map(b -> b.text)
                .filter(t -> t != null && !t.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    /** 이름표에 없는 단원의 제목 앞머리. 🔴 그럴듯한 이름을 «지어내지» 않는다. */
    private static final String UNNAMED_PREFIX = "(이름 미등록) ";

    /**
     * 대단원 챕터를 조회하거나 만든다.
     *
     * <p>🔴 <b>2026-09-09: 챕터 기준이 «유형» 에서 «대단원» 으로 바뀌었다.</b>
     * 예전에는 {@code question_topic_name}(「맞꼭지각(1)」)을 챕터로 썼다. 그러면
     * 챕터가 331개가 되고 그중 200개(60%)가 문제 3개 이하인데, 난이도 조정은
     * <b>챕터당 3문제 이상</b>이라야 시작하므로 그 챕터들은 난이도가 영원히 안 움직였다.
     * 실사용 통계 40건 중 3문제 이상 푼 챕터가 <b>1개</b>뿐이었다.
     *
     * <p>이제 {@code question_unit}(01~08)으로 묶는다. 실측상 이 값은
     * (학년·학기·영역·{@code question_topic} 코드 앞 4자리)와 <b>1:1</b> 이라 대단원이 맞다.
     * 챕터당 76~246문제가 되어 난이도 고리가 실제로 돈다.
     *
     * <p>🎯 부수 효과로 {@code orderNum} 문제도 사라진다. 예전에는 챕터 331개가
     * 순번 8종을 나눠 가져 표시 순서가 사실상 안 정해졌는데, 이제 챕터 8개에 순번 1~8 이다.
     */
    private Chapter getOrCreateChapter(Map<String, Chapter> cache,
                                       Grade grade,
                                       ProblemJson.QuestionInfo info,
                                       ChapterTitles titles) {
        String key = ChapterTitles.key(info.questionGrade, info.questionUnit);
        return cache.computeIfAbsent(key, k -> {
            String registered = titles.get(k);
            String title = registered != null
                    ? registered
                    : UNNAMED_PREFIX + info.questionUnit + "단원";
            if (registered == null) {
                log.warn("[DataLoader] 단원 이름표에 없습니다: {} - 제목을 「{}」 로 둡니다", k, title);
            }
            return chapterRepository.findByGradeAndTitle(grade, title)
                    .orElseGet(() -> {
                        Chapter created = chapterRepository.save(Chapter.builder()
                                .grade(grade)
                                .title(title)
                                .description(describe(info))
                                .orderNum(parseUnit(info.questionUnit))
                                .build());
                        log.info("[DataLoader] 챕터 생성: {} {} - {}", grade, info.questionUnit, title);
                        return created;
                    });
        });
    }

    /**
     * 챕터 설명문. 원본에 있는 값만 쓴다 — 「1학기 · 도형과 측정」.
     *
     * <p>실측상 {@code question_unit} 하나에 학기·영역이 각각 하나씩만 대응하므로
     * 이 문장은 그 단원의 모든 문제에서 같다.
     */
    private String describe(ProblemJson.QuestionInfo info) {
        if (info.questionTerm == null && info.questionSector2 == null) {
            return null;   // 🔴 모르는 것을 그럴듯한 문장으로 채우지 않는다
        }
        StringBuilder sb = new StringBuilder();
        if (info.questionTerm != null) {
            sb.append(info.questionTerm).append("학기");
        }
        if (info.questionSector2 != null && !info.questionSector2.isBlank()) {
            if (sb.length() > 0) {
                sb.append(" · ");
            }
            sb.append(info.questionSector2);
        }
        return sb.toString();
    }

    /** {@code question_unit} → 표시 순서. 숫자가 아니면 맨 뒤로 보낸다(문제를 버리지 않는다). */
    private int parseUnit(String unit) {
        try {
            return Integer.parseInt(unit.trim());
        } catch (NumberFormatException | NullPointerException e) {
            log.warn("[DataLoader] question_unit 이 숫자가 아닙니다: {} - 맨 뒤로 보냅니다", unit);
            return Integer.MAX_VALUE;
        }
    }

    /**
     * {@code question_difficulty} 점수 → Difficulty.
     *
     * <p>🔴 <b>이 자료에서는 상(HIGH) 이 나오지 않는다.</b> 실측 최댓값이 3 이라
     * {@code > 3} 조건에 걸리는 문제가 한 건도 없다. 난이도 축을 어떻게 정할지는
     * 사용자 결정 대기 항목이다 ({@code TODOS.md} 0절·8절) — 여기서 임의로 바꾸지 않는다.
     *
     * @param score 없으면 {@code null}. 🔴 「없음」을 0 으로 접지 않는다 —
     *              예전에는 {@code int} 라 필드가 없으면 0 이 되고, 조용히 하(LOW) 로 적재됐다.
     */
    private Difficulty difficultyFromScore(Integer score) {
        if (score == null) {
            log.warn("[DataLoader] question_difficulty 가 없습니다 - 중(MEDIUM) 으로 둡니다");
            return Difficulty.MEDIUM;   // 「모른다」를 가장 낮은 값으로 접지 않는다
        }
        if (score <= 2) return Difficulty.LOW;
        if (score <= 3) return Difficulty.MEDIUM;
        return Difficulty.HIGH;
    }

    // ─── JSON 역직렬화용 내부 클래스 ────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class ProblemJson implements HasId {
        public String id;

        @Override public String id() { return id; }

        @JsonProperty("question_info")
        public List<QuestionInfo> questionInfo;

        @JsonProperty("OCR_info")
        public List<OcrInfo> ocrInfo;

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class QuestionInfo {
            @JsonProperty("question_grade")      public String questionGrade;
            @JsonProperty("question_unit")       public String questionUnit;
            @JsonProperty("question_topic_name") public String questionTopicName;
            // 챕터 설명문(「1학기 · 도형과 측정」)에 쓴다. 🔴 Integer 다 — 없음과 0 을 구분한다.
            @JsonProperty("question_term")       public Integer questionTerm;
            @JsonProperty("question_sector2")    public String questionSector2;
            @JsonProperty("question_type1")      public String questionType1;
            @JsonProperty("question_step")       public String questionStep;
            // 🔴 Integer 다. int 면 필드가 없을 때 0 이 되어 «없음» 과 «0» 이 구분되지 않는다.
            @JsonProperty("question_difficulty") public Integer questionDifficulty;
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        static class OcrInfo {
            @JsonProperty("question_text")
            public String questionText;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class AnswerJson implements HasId {
        public String id;

        @Override public String id() { return id; }

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
