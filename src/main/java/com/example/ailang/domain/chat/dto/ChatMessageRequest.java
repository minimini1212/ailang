package com.example.ailang.domain.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class ChatMessageRequest {

    @NotBlank
    private String question;

    @NotBlank
    private String sessionId;
}
