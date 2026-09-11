package com.example.ailang.domain.chat.controller;

import com.example.ailang.domain.chat.dto.ChatMessageRequest;
import com.example.ailang.domain.chat.dto.ChatMessageResponse;
import com.example.ailang.global.client.AiServerClient;
import com.example.ailang.global.response.ResponseDTO;
import com.example.ailang.global.security.userdetails.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
            @AuthenticationPrincipal CustomUserDetails userDetails,
            @RequestBody @Valid ChatMessageRequest request) {
        // 🔴 학생 신원을 «서버가» 실어 보낸다. 클라이언트가 준 sessionId 만으로 대화를
        //    저장하면 남의 session_id 를 넣어 남의 대화를 읽을 수 있다.
        //    ⚠️ userId 는 요청 바디에서 받지 않는다 — 그러면 고친 의미가 없다.
        String answer = aiServerClient.requestChat(
                request.getQuestion(), request.getSessionId(), userDetails.getUserId());
        return ResponseEntity.ok(ResponseDTO.okWithData(
                new ChatMessageResponse(answer, request.getSessionId())));
    }
}
