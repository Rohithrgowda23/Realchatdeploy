package com.realchat.web;

import com.realchat.exception.ApiException;
import com.realchat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Map;

/** The sender is ALWAYS the authenticated principal; any user id in the payload is ignored. */
@Controller
@RequiredArgsConstructor
public class ChatWsController {
    public record SendPayload(Long conversationId, String content, Long replyToId, java.util.List<Long> mentions) {}
    public record TypingPayload(Long conversationId, boolean typing) {}

    private final ChatService chat;

    @MessageMapping("/chat.send")
    public void send(SendPayload p, Principal principal) {
        chat.send(p.conversationId(), Long.valueOf(principal.getName()), p.content(), p.replyToId(), p.mentions());
    }

    @MessageMapping("/chat.typing")
    public void typing(TypingPayload p, Principal principal) {
        chat.typing(p.conversationId(), Long.valueOf(principal.getName()), p.typing());
    }

    @MessageExceptionHandler
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public Map<String, String> onError(Exception e) {
        return Map.of("message", e instanceof ApiException ? e.getMessage() : "Request failed");
    }
}