package com.example.ailang.global.security.util;

import com.example.ailang.global.response.ResponseDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CustomResponseUtil {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void fail(HttpServletResponse response, String message, HttpStatus status) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(ResponseDTO.errorWithMessage(status, message)));
    }
}
