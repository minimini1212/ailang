package com.example.ailang.domain.user.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum Grade {
    ELEM_3("초등 3학년"),
    ELEM_4("초등 4학년"),
    ELEM_5("초등 5학년"),
    ELEM_6("초등 6학년"),
    MIDDLE_1("중학 1학년"),
    MIDDLE_2("중학 2학년"),
    MIDDLE_3("중학 3학년"),
    HIGH_1("고등 1학년");

    private final String displayName;
}
