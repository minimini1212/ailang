package com.example.ailang.domain.chat.controller;

import com.example.ailang.domain.chat.dto.ChatMessageRequest;
import com.example.ailang.domain.chat.dto.ChatMessageResponse;
import com.example.ailang.global.client.AiServerClient;
import com.example.ailang.global.response.ResponseDTO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI 챗봇 컨트롤러
 * - POST /api/ai/chat : FastAPI /ai/chat 프록시 (로그인 필요)
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiChatController {

    private final AiServerClient aiServerClient;

    @PostMapping("/chat")
    public ResponseEntity<ResponseDTO<ChatMessageResponse>> chat(
            @RequestBody @Valid ChatMessageRequest request) {
        String answer = aiServerClient.requestChat(request.getQuestion(), request.getSessionId());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                new ChatMessageResponse(answer, request.getSessionId())));
    }
}
