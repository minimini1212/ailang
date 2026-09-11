package com.example.ailang.domain.chapter.exception;

import com.example.ailang.global.exception.ApplicationException;
import com.example.ailang.global.exception.ErrorCode;

/**
 * 챕터를 찾을 수 없을 때 발생하는 예외
 */
public class ChapterNotFoundException extends ApplicationException {
    public ChapterNotFoundException() {
        super(ErrorCode.CHAPTER_NOT_FOUND);
    }
}
