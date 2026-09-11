package com.example.ailang.domain.user.dto.request;

import com.example.ailang.domain.user.entity.User;
import com.example.ailang.domain.user.entity.UserGrades;
import com.example.ailang.domain.user.enums.Grade;
import com.example.ailang.domain.user.exception.GradeRequiredException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 학년을 «고르는» 요청을 어떻게 받나.
 *
 * <p>🔴 이 검사가 막는 것: <b>학년을 안 보냈는데 서버가 아무 학년으로 채우는 것.</b>
 * 「모른다」를 값으로 접으면 그 학생은 엉뚱한 학년의 문제를 받고, 그 결과가 정답률에
 * 쌓여 난이도까지 틀어진다. 비면 400 으로 되돌려 보내야 한다.
 *
 * <p>🎯 이 요청이 있어야 하는 이유도 함께 박아 둔다 — 구글 가입 계정은 학년이 없어
 * 기능 다섯이 400 을 내는데, 여기가 <b>그 400 을 풀 수 있는 유일한 길</b>이다.
 *
 * <p>📌 Spring 컨텍스트도 DB 도 네트워크도 띄우지 않는다.
 */
class UpdateGradeRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private UpdateGradeRequest read(String json) throws Exception {
        return MAPPER.readValue(json, UpdateGradeRequest.class);
    }

    @Test
    @DisplayName("학년을 제대로 보내면 통과한다")
    void acceptsValidGrade() throws Exception {
        UpdateGradeRequest request = read("{\"grade\":\"MIDDLE_2\"}");

        assertThat(request.getGrade()).isEqualTo(Grade.MIDDLE_2);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("🔴 학년이 비면 위반이다 — 기본 학년으로 채우지 않는다")
    void rejectsMissingGrade() throws Exception {
        UpdateGradeRequest request = read("{}");

        Set<ConstraintViolation<UpdateGradeRequest>> violations = validator.validate(request);

        assertThat(request.getGrade()).isNull();
        assertThat(violations).hasSize(1);
        assertThat(violations.iterator().next().getPropertyPath()).hasToString("grade");
    }

    @Test
    @DisplayName("🔴 null 을 «명시적으로» 보내도 마찬가지다 — 학년을 지우는 길은 없다")
    void rejectsExplicitNull() throws Exception {
        UpdateGradeRequest request = read("{\"grade\":null}");

        assertThat(validator.validate(request)).hasSize(1);
    }

    @Test
    @DisplayName("🧭 없는 학년 이름은 읽는 단계에서 막힌다 — 문자열을 손으로 비교하지 않는다")
    void rejectsUnknownGradeName() {
        assertThatThrownBy(() -> read("{\"grade\":\"HIGH_3\"}"))
                .isInstanceOf(Exception.class);
        assertThatThrownBy(() -> read("{\"grade\":\"middle_2\"}"))
                .isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("🎯 학년을 정하면 400 을 내던 자리가 풀린다 — 이 요청이 있어야 하는 이유")
    void settingGradeUnblocksGradeDependentFeatures() throws Exception {
        User googleUser = User.builder().grade(null).build();
        assertThatThrownBy(() -> UserGrades.requireName(googleUser))
                .isInstanceOf(GradeRequiredException.class);

        googleUser.updateGrade(read("{\"grade\":\"ELEM_5\"}").getGrade());

        assertThatCode(() -> UserGrades.requireName(googleUser)).doesNotThrowAnyException();
        assertThat(UserGrades.requireName(googleUser)).isEqualTo("ELEM_5");
    }
}
